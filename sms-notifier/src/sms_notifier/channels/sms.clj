(ns sms-notifier.channels.sms
  (:require [sms-notifier.protocols :as p]
            [clj-http.client :as client]
            [cheshire.core :as json]
            [environ.core :refer [env]]
            [sms-notifier.utils :as utils]))

(defn- build-sms-body [template]
  (str "Alerta de Mudança de Categoria de Template!\n\n"
       "CANAL ATIVO: " (:wabaId template) "\n\n"
       "Nome do Template: " (utils/clean-template-name (:elementName template)) "\n"
       "Categoria Anterior: " (:oldCategory template) "\n"
       "Nova Categoria: " (:category template) "\n\n\n"
       "Atenciosamente,\n\n"
       "JM MASTER GROUP."))

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
  (send! [this contact-info template]
    (let [phone-numbers (let [p (:phone contact-info)]
                          (cond (coll? p) p, (nil? p) [], :else [p]))
          message-body  (build-sms-body template)
          template-id   (:id template)
          results       (atom [])]
      (if (empty? phone-numbers)
        {:status 400 :body "Nenhum número de telefone fornecido."}
        (do
          (doseq [phone phone-numbers]
            (println (str "  [SMS Channel] Disparando envio para o número: " phone))
            (let [response (send-sms-via-api phone message-body template-id)]
              (swap! results conj response)))
          (or (first (filter #(not= 200 (:status %)) @results))
              {:status 200 :body "Todos os SMS foram enviados com sucesso."}))))))

(defn make-sms-channel
  "Função construtora que cria uma instância do canal de SMS."
  []
  (->SmsChannel))
