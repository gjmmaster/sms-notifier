;; src/sms_notifier/channels/sms.clj
(ns sms-notifier.channels.sms
  (:require [sms-notifier.protocols :as p]
            [clj-http.client :as client]
            [cheshire.core :as json]
            [environ.core :refer [env]]))

(defn- send-sms-via-api
  "Função privada que contém a lógica original de chamada à API de SMS."
  [phone message external-id]
  (if-let [sms-api-url (env :sms-api-url)]
    (if-let [sms-api-token (env :sms-api-token)]
      (if-let [sms-api-user (env :sms-api-user)]
        (let [payload {:user sms-api-user,
                       :type 2,
                       :contact [{:number phone, :message message, :externalid external-id}],
                       :costcenter 0,
                       :fastsend 0,
                       :validate 0}
              response (try
                         (client/post (str sms-api-url "?token=" sms-api-token)
                                      {:body (json/generate-string payload),
                                       :content-type :json,
                                       :throw-exceptions false,
                                       :conn-timeout 300000,
                                       :socket-timeout 300000})
                         (catch Exception e
                           {:status 500 :body (str "Erro de conexão com a API de SMS: " (.getMessage e))}))]
          response)
        {:status 500 :body "Variável de ambiente SMS_API_USER não configurada."})
      {:status 500 :body "Variável de ambiente SMS_API_TOKEN não configurada."})
    {:status 500 :body "Variável de ambiente SMS_API_URL não configurada."}))

(defrecord SmsChannel []
  p/NotificationChannel
  (send! [this contact-info message-details]
    (let [phone-number (:phone contact-info)
          message-body (:body message-details)
          template-id (:template-id message-details)]
      (if phone-number
        (do
          (println (str "  [SMS Channel] Disparando envio para o número: " phone-number))
          (send-sms-via-api phone-number message-body template-id))
        {:status 400 :body "Número de telefone não fornecido para o canal de SMS."}))))

(defn make-sms-channel
  "Função construtora que cria uma instância do canal de SMS."
  []
  (->SmsChannel))
