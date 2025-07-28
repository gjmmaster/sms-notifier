;; src/sms_notifier/channels/email.clj
(ns sms-notifier.channels.email
  (:require [sms-notifier.protocols :as p]
            [clj-http.client :as client]
            [cheshire.core :as json]
            [environ.core :refer [env]]))

(defn- send-email-via-api [to-email to-name subject body external-id]
  (if-let [email-api-url (env :email-api-url)]
    (if-let [email-api-token (env :email-api-token)]
      (if-let [from-email (env :email-from-address)]
        (let [payload {:user       (env :email-api-user "default_user")
                       :from_email from-email
                       :from_name  (env :email-from-name "JM Master Contact")
                       :contact    [{:to_email   to-email
                                     :to_name    to-name
                                     :subject    subject
                                     :externalid external-id
                                     :body       body}]}
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
        {:status 500 :body "EMAIL_FROM_ADDRESS não configurada."})
      {:status 500 :body "EMAIL_API_TOKEN não configurada."})
    {:status 500 :body "EMAIL_API_URL não configurada."}))

(defrecord EmailChannel []
  p/NotificationChannel
  (send! [this contact-info message-details]
    (let [email-address (:email contact-info)
          contact-name (:name contact-info)
          subject (:subject message-details)
          body (:body message-details)
          template-id (:template-id message-details)]
      (if email-address
        (do
          (println (str "  [Email Channel] Disparando envio para o e-mail: " email-address))
          (send-email-via-api email-address contact-name subject body template-id))
        {:status 400 :body "Endereço de e-mail não fornecido para o canal de e-mail."}))))

(defn make-email-channel []
  (->EmailChannel))
