(ns sms-notifier.core
  (:require [clj-http.client :as client]
            [cheshire.core :as json]
            [environ.core :refer [env]]
            [org.httpkit.server :as server]
            [clojure.java.jdbc :as jdbc])
  (:gen-class))

(def sent-notifications-cache (atom #{}))
(def mock-customer-data (atom {}))
(def db-spec (env :database-url))

(defn load-keys-from-db!
  "Carrega todas as chaves do DB para o cache em memória na inicialização."
  []
  (if-not db-spec
    (println "ALERTA: DATABASE_URL não configurada. Cache de idempotência iniciará vazio.")
    (try
      (println "Carregando chaves de notificações do banco de dados para o cache em memória...")
      (let [keys (jdbc/query db-spec ["SELECT notification_key FROM sent_notifications"])]
        (if (seq keys)
          (let [key-set (->> keys (map :notification_key) (into #{}))]
            (reset! sent-notifications-cache key-set)
            (println (str "Sucesso! " (count key-set) " chaves carregadas para o cache.")))
          (println "Nenhuma chave encontrada no banco de dados. O cache iniciará vazio.")))
      (catch Exception e
        (println (str "ERRO CRÍTICO AO CARREGAR CACHE DO DB: O serviço pode enviar notificações duplicadas. Erro: " (.getMessage e)))))))

(defn save-notification-key!
  "Salva uma chave no banco de dados E no cache em memória para manter a consistência."
  [db-conn key]
  (jdbc/insert! db-conn :sent_notifications {:notification_key key})
  (swap! sent-notifications-cache conj key)
  (println (str "Chave " key " salva no DB e no cache em memória.")))

(defn process-notification
  "Processa uma notificação, usando o cache em memória para checagem e salvando no DB para persistência."
  [template]
  (let [waba-id (:wabaId template)
        template-id (:id template)
        new-category (:category template)
        notification-key (str template-id "_" new-category)]

    (if (@sent-notifications-cache notification-key)
      (println (str "Notificação para " notification-key " já processada (cache). Ignorando."))
      (if-let [phone (get-contact-phone waba-id)]
        (let [message (str "Alerta de Mudança de Categoria de Template!\n"
                           "  - WABA ID: " waba-id "\n"
                           "  - Template: " (:elementName template) " (" template-id ")\n"
                           "  - Categoria Anterior: " (:oldCategory template) "\n"
                           "  - Nova Categoria: " new-category)
              response (send-sms-via-api phone message template-id)]
          (if (= (:status response) 200)
            (do
              (println (str "SMS enviado para " phone ". Template: " (:elementName template)))
              (try
                (jdbc/with-db-connection [db-conn db-spec]
                  (save-notification-key! db-conn notification-key))
                (catch Exception e
                  (println (str "ERRO DE BANCO DE DADOS ao salvar chave: " (.getMessage e))))))
            (println (str "Falha ao enviar SMS para " phone ". Resposta da API: " (:body response)))))
        (println (str "Aviso: Telefone não encontrado para o WABA ID: " waba-id))))))

(defn parse-customer-data []
  (if-let [customer-data-json (env :mock-customer-data)]
    (try
      (let [parsed-data (json/parse-string customer-data-json true)]
        (reset! mock-customer-data parsed-data)
        (println "Dados de clientes mockados carregados."))
      (catch Exception e
        (println (str "Erro ao parsear MOCK_CUSTOMER_DATA: " (.getMessage e)))))
    (println "Aviso: MOCK_CUSTOMER_DATA não definida.")))

(defn get-contact-phone [waba-id]
  (get @mock-customer-data (keyword waba-id)))

(defn send-sms-via-api [phone message external-id]
  (if-let [sms-api-url (env :sms-api-url)]
    (if-let [sms-api-token (env :sms-api-token)]
      (if-let [sms-api-user (env :sms-api-user)]
        (let [payload {:user sms-api-user, :type 2, :contact [{:number phone, :message message, :externalid external-id}], :costcenter 0, :fastsend 0, :validate 0}
              response (try
                         (client/post (str sms-api-url "?token=" sms-api-token)
                                      {:body (json/generate-string payload), :content-type :json, :throw-exceptions false, :conn-timeout 5000, :socket-timeout 5000})
                         (catch Exception e
                           {:status 500 :body (str "Erro de conexão: " (.getMessage e))}))]
          response)
        {:status 500 :body "SMS_API_USER não configurada."})
      {:status 500 :body "SMS_API_TOKEN não configurada."})
    {:status 500 :body "SMS_API_URL não configurada."}))

(defn fetch-and-process-templates []
  (if-let [watcher-url (env :watcher-url)]
    (try
      (println (str "Consultando " watcher-url "/changed-templates..."))
      (let [response (client/get (str watcher-url "/changed-templates") {:as :json, :throw-exceptions false, :conn-timeout 5000, :socket-timeout 5000})
            templates (get-in response [:body])]
        (if (and (= (:status response) 200) (seq templates))
          (do
            (println (str "Recebidos " (count templates) " templates alterados."))
            (doseq [template templates] (process-notification template)))
          (println "Nenhum template alterado encontrado.")))
      (catch Exception e
        (println (str "Erro ao conectar com o notification-watcher: " (.getMessage e)))))
    (println "ALERTA: WATCHER_URL não configurada.")))

(defn start-notifier-loop!
  "Inicia o loop principal do serviço em uma thread separada."
  []
  (println "Iniciando o loop do SMS Notifier...")
  (future
    (Thread/sleep 30000)
    (loop []
      (fetch-and-process-templates)
      ;; --- ALTERAÇÃO DO INTERVALO DE CONSULTA ---
      (Thread/sleep 240000) ; Espera 4 minutos (240.000 ms)
      (recur))))

(defn app-handler [request]
  {:status 200, :headers {"Content-Type" "text/plain; charset=utf-8"}, :body "Serviço SMS Notifier (com DB-cache) está no ar."})

(defn -main
  "Ponto de entrada principal da aplicação."
  [& args]
  (let [port (Integer/parseInt (env :port "8080"))]
    (println "======================================")
    (println "  SMS Notifier v0.3.1 (Cache Híbrido, 4 min)")
    (println "======================================")
    (load-keys-from-db!)
    (parse-customer-data)
    (start-notifier-loop!)
    (server/run-server app-handler {:port port})
    (println (str "Servidor web iniciado na porta " port ". Worker ativo."))))
