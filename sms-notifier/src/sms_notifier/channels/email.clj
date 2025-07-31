;; src/sms_notifier/channels/email.clj
(ns sms-notifier.channels.email
  (:require [sms-notifier.protocols :as p]
            [clj-http.client :as client]
            [cheshire.core :as json]
            [environ.core :refer [env]]))

(defn- send-email-via-api [to-email to-name subject body external-id]
  (if-let [email-api-url (env :email-api-url "https://www.apiswagger.com.br/api/email/send_single_email_to_single_or_multiple_recipients")]
    (if-let [email-api-token (env :email-api-token)]
      (let [payload {:user       (env :email-api-user)
                     :from_email "noreplay@jmmaster.com"
                     :from_name  "JM Master Group"
                     :contact    [{:to_email   to-email
                                   :to_name    to-name
                                   :subject    subject
                                   :externalid external-id}]
                     :body       body}
            response (try
                       (client/post (str email-api-url "?token=" email-api-token)
                                    {:body         (json/generate-string payload)
                                     :content-type :json
                                     :throw-exceptions false
                                     :conn-timeout 300000
                                     :socket-timeout 300000})
                       (catch Exception e
                         {:status 500 :body (str "Erro de conexão com API de Email: " (.getMessage e))}))]
        response)
      {:status 500 :body "EMAIL_API_TOKEN não configurada."})
    {:status 500 :body "EMAIL_API_URL não configurada."}))

(defrecord EmailChannel []
  p/NotificationChannel
  (send! [this contact-info message-details]
    (let [email-addresses (let [e (:email contact-info)]
                            (cond
                              (coll? e) e
                              (nil? e) []
                              :else [e]))
          contact-name    (:name contact-info)
          subject         (:subject message-details)
          body            (:body message-details)
          template-id     (:template-id message-details)
          results         (atom [])]
      (if (empty? email-addresses)
        {:status 400 :body "Nenhum endereço de e-mail fornecido."}
        (do
          (doseq [email email-addresses]
            (println (str "  [Email Channel] Disparando envio para o e-mail: " email))
            (let [response (send-email-via-api email contact-name subject body template-id)]
              (swap! results conj response)))
          ;; Return a summary: the first failure or a generic success
          (or (first (filter #(not= 200 (:status %)) @results))
              {:status 200 :body "Todos os emails foram enviados com sucesso."}))))))

(defn make-email-channel []
  (->EmailChannel))
