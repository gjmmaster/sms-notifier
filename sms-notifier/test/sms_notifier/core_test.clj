(ns sms-notifier.core-test
  (:require [clojure.test :refer :all]
            [sms-notifier.core :refer :all]
            [clj-http.fake :as fakes]))

(deftest parse-customer-data-test
  (testing "Parsing MOCK_CUSTOMER_DATA"
    (with-redefs [env {:mock-customer-data "{\"waba_id_1\": \"+5511999998888\", \"waba_id_2\": \"+5521888887777\"}"}]
      (parse-customer-data)
      (is (= @mock-customer-data {:waba_id_1 "+5511999998888", :waba_id_2 "+5521888887777"})))

    (with-redefs [env {:mock-customer-data "invalid-json"}]
      (parse-customer-data)
      (is (= @mock-customer-data {})))

    (with-redefs [env {}]
      (parse-customer-data)
      (is (= @mock-customer-data {})))))

(deftest get-contact-phone-test
  (testing "Getting contact phone"
    (with-redefs [mock-customer-data (atom {:waba_id_1 "+5511999998888"})]
      (is (= (get-contact-phone "waba_id_1") "+5511999998888"))
      (is (nil? (get-contact-phone "waba_id_2"))))))

(deftest process-notification-test
  (testing "Processing notifications"
    (let [template {:wabaId "waba_id_1"
                    :id "template_1"
                    :elementName "template_element"
                    :category "NEW_CATEGORY"
                    :oldCategory "OLD_CATEGORY"}]
      (testing "Idempotency"
        (with-redefs [sent-notifications-cache (atom #{"template_1_NEW_CATEGORY"})
                      [send-sms-via-api (fn [& args] (is false "send-sms-via-api should not be called"))]]
          (process-notification template)))

      (testing "Contact not found"
        (with-redefs [sent-notifications-cache (atom #{})
                      [get-contact-phone (fn [waba-id] (is (= waba-id "waba_id_1")) nil)]
                      [send-sms-via-api (fn [& args] (is false "send-sms-via-api should not be called"))]]
          (process-notification template)))

      (testing "Successful processing"
        (let [sms-sent (atom false)]
          (with-redefs [sent-notifications-cache (atom #{})
                        [get-contact-phone (fn [waba-id] (is (= waba-id "waba_id_1")) "+5511999998888")]
                        [send-sms-via-api (fn [phone message template-id]
                                           (is (= phone "+5511999998888"))
                                           (is (= template-id "template_1"))
                                           (reset! sms-sent true)
                                           {:status 200 :body ""})]]
            (process-notification template)
            (is @sms-sent)
            (is (contains? @sent-notifications-cache "template_1_NEW_CATEGORY"))))))))

(deftest fetch-and-process-templates-test
  (testing "Fetching and processing templates"
    (with-redefs [env {:watcher-url "http://watcher.example.com"}]
      (testing "Successful fetch and process"
        (let [processed-notifications (atom #{})]
          (with-redefs [[process-notification (fn [template] (swap! processed-notifications conj (:id template)))]]
            (fakes/with-fakes { "http://watcher.example.com/changed-templates"
                               (fn [req] {:status 200 :headers {} :body "[{\"id\": \"t1\"}, {\"id\": \"t2\"}]"})}
              (fetch-and-process-templates))
            (is (= @processed-notifications #{"t1" "t2"})))))

      (testing "Watcher returns empty list"
        (let [processed-notifications (atom #{})]
          (with-redefs [[process-notification (fn [template] (swap! processed-notifications conj (:id template)))]]
            (fakes/with-fakes { "http://watcher.example.com/changed-templates"
                               (fn [req] {:status 200 :headers {} :body "[]"}) }
              (fetch-and-process-templates))
            (is (empty? @processed-notifications))))))))
