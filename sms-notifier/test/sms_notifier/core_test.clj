(ns sms-notifier.core-test
  (:require [clojure.test :refer :all]
            [sms-notifier.core :refer :all]
            [clj-http.fake :as fakes]
            [environ.core :refer [env]]
            [sms-notifier.protocols :as p]
            [clojure.java.jdbc :as jdbc]))

;; Fixture to reset state for each test
(use-fixtures :each
  (fn [f]
    (reset! sent-notifications-cache #{})
    (reset! mock-customer-data {})
    (reset! circuit-breaker-state {:state :closed, :failures 0, :last-failure-time nil})
    (f)))

;; --- PARSE CUSTOMER DATA TESTS ---
(deftest parse-customer-data-test-success
  (testing "Parsing valid MOCK_CUSTOMER_DATA"
    (with-redefs [env (fn [key] (get {:mock-customer-data "{\"waba_id_1\": {\"phone\": \"+5511999998888\", \"email\": \"test1@test.com\"}}"} key))]
      (parse-customer-data)
      (is (= {:waba_id_1 {:phone "+5511999998888", :email "test1@test.com"}} @mock-customer-data)))))

(deftest parse-customer-data-test-invalid
  (testing "Parsing invalid MOCK_CUSTOMER_DATA"
    ;; This test assumes that a failed parse leaves the atom unchanged.
    (reset! mock-customer-data {:old :data})
    (with-redefs [env (fn [key] (get {:mock-customer-data "invalid-json"} key))]
      (parse-customer-data)
      (is (= {:old :data} @mock-customer-data)))))

(deftest parse-customer-data-test-missing
  (testing "Parsing with missing MOCK_CUSTOMER_DATA"
    (reset! mock-customer-data {:old :data})
    (with-redefs [env (fn [key] (get {} key))]
      (parse-customer-data)
      (is (= {:old :data} @mock-customer-data)))))

;; --- GET CONTACT INFO TEST ---
(deftest get-contact-info-test
  (testing "Getting contact info"
    (reset! mock-customer-data {:waba_id_1 {:phone "+5511999998888" :email "test@test.com"}})
    (is (= {:phone "+5511999998888" :email "test@test.com"} (get-contact-info "waba_id_1")))
    (is (nil? (get-contact-info "waba_id_2")))))

;; --- MOCK CHANNEL FOR TESTING ---
(defrecord MockChannel [sent-atom]
  p/NotificationChannel
  (send! [_ _ _]
    (swap! sent-atom inc)
    {:status 200 :body "Mocked!"}))

;; --- PROCESS NOTIFICATION TESTS ---
(deftest process-notification-test-idempotency
  (testing "Idempotency"
    (let [template {:wabaId "waba_id_1", :id "template_1", :category "NEW_CATEGORY"}
          notifications-sent-count (atom 0)
          mock-channel (->MockChannel notifications-sent-count)]
      (with-redefs [active-channels [mock-channel]
                    sent-notifications-cache (atom #{"template_1_NEW_CATEGORY"})]
        (process-notification template (atom {}))
        (is (zero? @notifications-sent-count))))))

(deftest process-notification-test-no-contact
  (testing "Contact not found"
    (let [template {:wabaId "waba_id_1", :id "template_1", :category "NEW_CATEGORY"}
          notifications-sent-count (atom 0)
          mock-channel (->MockChannel notifications-sent-count)]
      (with-redefs [active-channels [mock-channel]
                    get-contact-info (fn [waba-id] (is (= waba-id "waba_id_1")) nil)]
        (process-notification template (atom {}))
        (is (zero? @notifications-sent-count))))))

(deftest process-notification-test-success
  (testing "Successful processing with multiple contacts"
    (let [template {:wabaId "waba_id_1", :id "template_1", :category "NEW_CATEGORY"}
          notifications-sent-count (atom 0)
          mock-channel (->MockChannel notifications-sent-count)
          saved-keys (atom #{})
          contact-list [{:name "Contact 1", :phone "111"}
                        {:name "Contact 2", :email "contact2@a.com"}]]
      (with-redefs [active-channels [mock-channel mock-channel] ; 2 active channels
                    get-contact-info (fn [_] contact-list) ; 2 contacts in the list
                    sms-notifier.core/persist-notification-key! (fn [key] (swap! saved-keys conj key))]
        (process-notification template (atom {}))
        ;; Each contact should be notified by each channel
        (is (= 4 @notifications-sent-count)) ; 2 contacts * 2 channels = 4
        (is (contains? @saved-keys "template_1_NEW_CATEGORY"))))))

;; --- FETCH AND PROCESS TEST ---
(deftest fetch-and-process-templates-test
  (testing "Fetching and processing templates"
    (with-redefs [env (fn [key] (get {:watcher-url "http://watcher.example.com"} key))]
      (testing "Successful fetch and process"
        (let [processed-notifications (atom #{})]
          (with-redefs [process-notification (fn [template spam-counter] (swap! processed-notifications conj (:id template)))]
            (fakes/with-fake-routes { "http://watcher.example.com/changed-templates"
                               (fn [req] {:status 200 :headers {} :body "[{\"id\": \"t1\"}, {\"id\": \"t2\"}]"})}
              (fetch-and-process-templates))
            (is (= #{"t1" "t2"} @processed-notifications))))))))

;; --- SPAM PROTECTION TEST ---
(deftest spam-protection-test
  (testing "Spam protection"
    (let [contact-info {:phone "+5511999998888", :email "test@test.com"}
          spam-counter (atom {})
          notifications-sent-count (atom 0)
          mock-channel (->MockChannel notifications-sent-count)
          template-generator (fn [i] {:wabaId "waba_id_1", :id (str "template_" i), :category "NEW_CATEGORY"})]
      (with-redefs [active-channels [mock-channel]
                    ;; Return the contact object in a list to test the new structure
                    get-contact-info (fn [_] [contact-info])
                    sms-notifier.core/persist-notification-key! (fn [key] (swap! sent-notifications-cache conj key))]

        ; Send 5 messages, which should be allowed
        (doseq [i (range 5)]
          (process-notification (template-generator i) spam-counter))
        (is (= 5 @notifications-sent-count))

        ; The 6th message should be blocked
        (process-notification (template-generator 6) spam-counter)
        (is (= 5 @notifications-sent-count)) ; Should not have incremented
        (is (= 5 (get @spam-counter contact-info)))))))
