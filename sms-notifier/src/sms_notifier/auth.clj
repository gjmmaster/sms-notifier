(ns sms-notifier.auth
  (:require [clj-http.client :as client]
            [cheshire.core :as json]
            [environ.core :refer [env]]))

;; Atom para guardar o token e sua data de expiração (em milissegundos)
(def whatsapp-token-cache (atom {:token nil, :expires-at 0}))

(defn refresh-token!
  "Executa a chamada para renovar o token e atualiza o cache."
  []
  (println "INFO: Token do WhatsApp expirado ou ausente. Renovando...")
  (try
    (if-let [auth-url (env :whatsapp-auth-url)]
      (let [response (client/post auth-url
                                  {:form-params {:usuario (env :whatsapp-auth-user)
                                                 :senha (env :whatsapp-auth-password)}
                                   :content-type :x-www-form-urlencoded
                                   :as :json
                                   :throw-exceptions false})
            
            new-token (get-in response [:body :data :token])
            
            ;; Como a API não informa a validade, assumimos 23 horas (em segundos)
            expires-in-seconds 82800
            
            ;; Convertendo segundos para milissegundos
            expires-at (+ (System/currentTimeMillis) (* expires-in-seconds 1000))]

        (if new-token
          (do
            (println (str "INFO: Token do WhatsApp renovado com sucesso. Válido por aproximadamente 23 horas."))
            (reset! whatsapp-token-cache {:token new-token, :expires-at expires-at})
            new-token)
          (do
            (println (str "ERRO: Falha ao extrair token da resposta da API. Resposta recebida: " (:body response)))
            nil)))
      (do
        (println "ERRO: Variável de ambiente WHATSAPP_AUTH_URL não configurada.")
        nil))
    (catch Exception e
      (println (str "ERRO CRÍTICO ao renovar token do WhatsApp: " (.getMessage e)))
      nil)))

(defn get-valid-token!
  "Verifica se o token em cache é válido. Se estiver próximo de expirar, renova-o."
  []
  (let [now (System/currentTimeMillis)
        cached-token @whatsapp-token-cache
        expiration-threshold (+ now 300000)] ; 5 minutos de margem
        
    (if (or (nil? (:token cached-token))
            (< (:expires-at cached-token) expiration-threshold))
      (refresh-token!)
      (:token cached-token))))
