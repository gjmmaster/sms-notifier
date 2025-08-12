;; src/sms_notifier/channels/whatsapp.clj
(ns sms-notifier.channels.whatsapp
  (:require [sms-notifier.protocols :as p]
            [clj-http.client :as client]
            [cheshire.core :as json]
            [environ.core :refer [env]]
            [sms-notifier.utils :as utils]))

(defn- build-whatsapp-payload [contact-phone template]
  "Constrói o corpo (payload) da requisição para a API de WhatsApp."
  {:templateID (env :whatsapp-template-id "970b43ad-4f93-4e53-9065-408009b140d2") ; Template ID padrão
   :telefone contact-phone
   :grupo "comercial"
   :vip true
   :variaveis {:mensagem {:additionalProp1 (:wabaId template)
                           :additionalProp2 (utils/clean-template-name (:elementName template))
                           :additionalProp3 (:oldCategory template)
                           :additionalProp4 (:category template)}}
   :internacional true})

(defn- send-whatsapp-via-api [contact-phone template]
  "Função privada para enviar a notificação via API de WhatsApp."
  (if-let [api-url (env :whatsapp-api-url)]
    (if-let [api-token (env :whatsapp-api-token)]
      (let [payload (build-whatsapp-payload contact-phone template)
            response (try
                       (client/post api-url
                                    {:headers {:Authorization (str "Token " api-token)}
                                     :body (json/generate-string payload)
                                     :content-type :json
                                     :throw-exceptions false
                                     :conn-timeout 30000
                                     :socket-timeout 30000})
                       (catch Exception e
                         {:status 500 :body (str "Erro de conexão com a API de WhatsApp: " (.getMessage e))}))]
        (if (>= (:status response) 200)
          (println (str "Protocolo WhatsApp recebido para " contact-phone ": " (get-in response [:body :data :protocol])))
          (println (str "Falha ao enviar WhatsApp para " contact-phone ". Resposta: " (:body response))))
        response)
      {:status 500 :body "Variável de ambiente WHATSAPP_API_TOKEN não configurada."})
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
          ;; Retorna o primeiro erro encontrado, ou sucesso se todos funcionaram.
          (or (first (filter #(not= 200 (:status %)) @results))
              {:status 200 :body "Todos as notificações de WhatsApp foram enviadas com sucesso."})))))

(defn make-whatsapp-channel []
  (->WhatsAppChannel))
