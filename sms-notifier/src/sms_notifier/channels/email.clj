(ns sms-notifier.channels.email
  (:require [sms-notifier.protocols :as p]
            [clj-http.client :as client]
            [cheshire.core :as json]
            [environ.core :refer [env]]
            [sms-notifier.utils :as utils]))

(defn- build-email-subject [template]
  (str "Alerta de Mudança de Categoria: " (utils/clean-template-name (:elementName template))))

(defn- build-email-body [template]
  (str "<h1>Alerta de Mudança de Categoria!</h1>"
       "<p><b>CANAL ATIVO:</b> " (:wabaId template) "</p>"
       "<ul>"
       "<li><b>Nome do Template:</b> " (:elementName template) "</li>"
       "<li><b>Categoria Anterior:</b> " (:oldCategory template) "</li>"
       "<li><b>Nova Categoria:</b> " (:category template) "</li>"
       "</ul>"
       "<p>Atenciosamente,<br>JM MASTER GROUP.</p>"))

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
  (send! [this contact-info template]
    (let [email-addresses (let [e (:email contact-info)]
                            (cond (coll? e) e, (nil? e) [], :else [e]))
          contact-name    (:name contact-info)
          subject         (build-email-subject template)
          body            (build-email-body template)
          template-id     (:id template)
          results         (atom [])]
      (if (empty? email-addresses)
        {:status 400 :body "Nenhum endereço de e-mail fornecido."}
        (do
          (doseq [email email-addresses]
            (println (str "  [Email Channel] Disparando envio para o e-mail: " email))
            (let [response (send-email-via-api email contact-name subject body template-id)]
              (swap! results conj response)))
          (or (first (filter #(not= 200 (:status %)) @results))
              {:status 200 :body "Todos os emails foram enviados com sucesso."}))))))

(defn make-email-channel []
  (->EmailChannel))
