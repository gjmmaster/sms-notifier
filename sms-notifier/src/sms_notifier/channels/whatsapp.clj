(ns sms-notifier.channels.whatsapp
  (:require [sms-notifier.protocols :as p]
            [clj-http.client :as client]
            [cheshire.core :as json]
            [environ.core :refer [env]]
            [sms-notifier.utils :as utils]
            [sms-notifier.auth :as auth]))

(defn- build-whatsapp-payload [contact-phone template]
   {:templateID (env :whatsapp-template-id "970b43ad-4f93-4e53-9065-408009b140d2")
    :telefone contact-phone
    :grupo "comercial"
    :vip true
    :variaveis {:mensagem
                {:additionalProp1 (:wabaId template)
                 :additionalProp2 (utils/clean-template-name (:elementName template))
                 :additionalProp3 (:oldCategory template)
                 :additionalProp4 (:category template)}}
    :internacional true})


(defn- send-attempt!
  "Função auxiliar que realiza uma única tentativa de envio."
  [api-url contact-phone template]
  (if-let [api-token (auth/get-valid-token!)]
    (let [payload (build-whatsapp-payload contact-phone template)]
      (try
        (client/post api-url
                     {:headers      {:Authorization (str "Token " api-token)}
                      :body         (json/generate-string payload)
                      :content-type :json
                      :as           :json
                      :throw-exceptions false
                      :conn-timeout 30000
                      :socket-timeout 30000})
        (catch Exception e
          {:status 500 :body (str "Erro de conexão com a API de WhatsApp: " (.getMessage e))})))
    {:status 500 :body "Falha ao obter um token de autenticação do gerenciador."}))

(defn- send-whatsapp-via-api [contact-phone template]
  "Envia a notificação com lógica de 'tentativa e recuperação'."
  (if-let [api-url (env :whatsapp-api-url)]
    (let [first-attempt-response (send-attempt! api-url contact-phone template)]
      ;; --- LÓGICA DE AUTO-RECUPERAÇÃO CONFIRMADA ---
      ;; Se a API retornar 401, o token está inválido.
      (if (= (:status first-attempt-response) 401)
        (do
          (println "INFO: Falha na autenticação (status 401 - Token Inválido). Forçando renovação e tentando novamente...")
          (auth/refresh-token!) ; Força a busca por um novo token
          (send-attempt! api-url contact-phone template)) ; Tenta uma segunda e última vez
        
        first-attempt-response))
    {:status 500 :body "Variável de ambiente WHATSAPP_API_URL não configurada."}))


(defrecord WhatsAppChannel []
  p/NotificationChannel
  (send! [this contact-info template]
    (let [whatsapp-numbers (let [w (:whatsapp-number contact-info)]
                             (cond (coll? w) w, (nil? w) [], :else [w]))
          results (atom [])]
      (if (empty? whatsapp-numbers)
        {:status 400 :body "Nenhum número de WhatsApp fornecido."}
        (do
          (doseq [whatsapp-number whatsapp-numbers]
            (println (str "  [WhatsApp Channel] Disparando envio para o número: " whatsapp-number))
            (let [response (send-whatsapp-via-api whatsapp-number template)]
              (swap! results conj response)))
          (or (first (filter #(not (and (>= (:status %) 200) (< (:status %) 300))) @results))
              {:status 200 :body "Todos as notificações de WhatsApp foram enviadas com sucesso."}))))))


(defn make-whatsapp-channel []
  (->WhatsAppChannel))
