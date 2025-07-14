(ns sms-notifier-prototype.core
  (:require [clj-http.client :as client]
            [cheshire.core :as json]
            [environ.core :refer [env]])
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

(defn process-notification
  "Processa uma notificação, simula o envio e atualiza o cache de idempotência."
  [template]
  (let [waba-id (:wabaId template)
        template-id (:id template)
        new-category (:category template)
        notification-key (str template-id "_" new-category)]

    (if (@sent-notifications-cache notification-key)
      (println (str "Notificação para " notification-key " já processada. Ignorando."))
      (if-let [phone (get-contact-phone waba-id)]
        (do
          (println "--------------------------------------------------")
          (println "SIMULANDO ENVIO DE SMS")
          (println (str "PARA: " phone))
          (println "MENSAGEM: Alerta de Mudança de Categoria de Template!")
          (println (str "  - WABA ID: " waba-id))
          (println (str "  - Template: " (:elementName template) " (" template-id ")"))
          (println (str "  - Categoria Anterior: " (:oldCategory template)))
          (println (str "  - Nova Categoria: " new-category))
          (println "--------------------------------------------------")
          (swap! sent-notifications-cache conj notification-key))
        (println (str "Aviso: Telefone de contato não encontrado para o WABA ID: " waba-id))))))

(defn fetch-and-process-templates
  "Busca templates alterados do notification-watcher e inicia o processamento."
  []
  (let [watcher-url (env :watcher-url "http://localhost:8080")]
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
        (println (str "Erro ao conectar com o notification-watcher: " (.getMessage e)))))))

(defn start-notifier-loop!
  "Inicia o loop principal do serviço em uma thread separada."
  []
  (println "Iniciando o loop do SMS Notifier Prototype...")
  (future
    (loop []
      (fetch-and-process-templates)
      (Thread/sleep 60000) ; Espera 1 minuto
      (recur))))

(defn -main
  "Ponto de entrada principal da aplicação."
  [& args]
  (println "======================================")
  (println "  SMS Notifier Prototype v0.1.0")
  (println "======================================")
  (parse-customer-data)
  (start-notifier-loop!))
