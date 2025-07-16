(ns sms-notifier.core
  (:require [clj-http.client :as client]
            [cheshire.core :as json]
            [environ.core :refer [env]]
            [org.httpkit.server :as server])
  (:gen-class))

;; Atom para garantir a idempotência. Armazenará chaves únicas para notificações já processadas.
(def sent-notifications-cache (atom #{}))

;; Atom para armazenar os dados de contato mockados.
(def mock-customer-data (atom {}))

(defn parse-customer-data
  "Lê e parseia a string JSON da variável de ambiente MOCK_CUSTOMER_DATA."
  []
  (if-let [customer-data-json (env :mock-customer-data)]
    (try
      (let [parsed-data (json/parse-string customer-data-json true)]
        (reset! mock-customer-data parsed-data)
        (println "Dados de clientes mockados carregados com sucesso."))
      (catch Exception e
        (println (str "Erro ao parsear MOCK_CUSTOMER_DATA: " (.getMessage e)))))
    (println "Aviso: Variável de ambiente MOCK_CUSTOMER_DATA não definida.")))

(defn get-contact-phone
  "Busca o telefone de contato para um WABA ID específico nos dados mockados."
  [waba-id]
  (get @mock-customer-data (keyword waba-id)))

(defn send-sms-via-api
  "Envia uma notificação por SMS utilizando a API real."
  [phone message external-id]
  (if-let [sms-api-url (env :sms-api-url)]
    (if-let [sms-api-token (env :sms-api-token)]
      (if-let [sms-api-user (env :sms-api-user)]
        (let [payload {:user sms-api-user
                       :type 2
                       :contact [{:number phone
                                  :message message
                                  :externalid external-id}]
                       :costcenter 0
                       :fastsend 0
                       :validate 0}
              response (try
                         (client/post (str sms-api-url "?token=" sms-api-token)
                                      {:body (json/generate-string payload)
                                       :content-type :json
                                       :throw-exceptions false
                                       :conn-timeout 5000
                                       :socket-timeout 5000})
                         (catch Exception e
                           {:status 500 :body (str "Erro de conexão: " (.getMessage e))}))]
          response)
        {:status 500 :body "Variável de ambiente SMS_API_USER não configurada."})
      {:status 500 :body "Variável de ambiente SMS_API_TOKEN não configurada."})
    {:status 500 :body "Variável de ambiente SMS_API_URL não configurada."}))

(defn process-notification
  "Processa uma notificação, envia um SMS via API e atualiza o cache de idempotência."
  [template]
  (let [waba-id (:wabaId template)
        template-id (:id template)
        new-category (:category template)
        notification-key (str template-id "_" new-category)]

    (if (@sent-notifications-cache notification-key)
      (println (str "Notificação para " notification-key " já processada. Ignorando."))
      (if-let [phone (get-contact-phone waba-id)]
        (let [message (str "Alerta de Mudança de Categoria de Template!\n"
                           "  - WABA ID: " waba-id "\n"
                           "  - Template: " (:elementName template) " (" template-id ")\n"
                           "  - Categoria Anterior: " (:oldCategory template) "\n"
                           "  - Nova Categoria: " new-category)
              response (send-sms-via-api phone message template-id)]
          (if (= (:status response) 200)
            (do
              (println (str "SMS enviado com sucesso para " phone ". Template: " (:elementName template)))
              (println (str "  - Resposta da API: " (if (map? (:body response))
                                                     (json/generate-string (:body response))
                                                     (:body response))))
              (swap! sent-notifications-cache conj notification-key))
            (do
              (println (str "Falha ao enviar SMS para " phone ". Template: " (:elementName template)))
              (println (str "  - Status: " (:status response)))
              (println (str "  - Resposta da API: " (:body response))))))
        (println (str "Aviso: Telefone de contato não encontrado para o WABA ID: " waba-id))))))

(defn fetch-and-process-templates
  "Busca templates alterados do notification-watcher e inicia o processamento."
  []
  (if-let [watcher-url (env :watcher-url)]
    ;; O código abaixo só será executado se a WATCHER_URL estiver definida.
    (do
      (println (str "Consultando " watcher-url "/changed-templates..."))
      (try
        (let [response (client/get (str watcher-url "/changed-templates")
                                   {:as :json
                                    :throw-exceptions false
                                    :conn-timeout 5000
                                    :socket-timeout 5000})
              templates (get-in response [:body])]
          (if (and (= (:status response) 200) (seq templates))
            (do
              (println (str "Recebidos " (count templates) " templates alterados."))
              (doseq [template templates]
                (process-notification template)))
            (println "Nenhum template alterado encontrado ou erro na resposta.")))
        (catch Exception e
          (println (str "Erro ao conectar com o notification-watcher: " (.getMessage e))))))

    ;; Este bloco será executado se a WATCHER_URL não estiver definida.
    (println "ALERTA: A variável de ambiente WATCHER_URL não está configurada. A consulta ao watcher foi ignorada.")))

(defn start-notifier-loop!
  "Inicia o loop principal do serviço em uma thread separada."
  []
  (println "Iniciando o loop do SMS Notifier Prototype...")
  (future
    (Thread/sleep 30000) ; Atraso inicial para dar tempo a outras partes do sistema de inicializarem
    (loop []
      (fetch-and-process-templates)
      (Thread/sleep 60000) ; Espera 1 minuto
      (recur))))

(defn app-handler [request]
  "Handler HTTP simples para o Web Service."
  {:status 200
   :headers {"Content-Type" "text/plain; charset=utf-8"}
   :body "Serviço SMS Notifier Prototype está no ar e o worker está rodando em background."})

(defn -main
  "Ponto de entrada principal da aplicação."
  [& args]
  (let [port (Integer/parseInt (env :port "8080"))]
    (println "======================================")
    (println "  SMS Notifier Prototype v0.2.0")
    (println "======================================")
    (parse-customer-data)
    (start-notifier-loop!)
    (server/run-server app-handler {:port port})
    (println (str "Servidor web iniciado na porta " port ". O worker de notificação está ativo."))))
