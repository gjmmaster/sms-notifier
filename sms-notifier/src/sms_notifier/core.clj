;; src/sms_notifier/core.clj
(ns sms-notifier.core
  (:require [clj-http.client :as client]
            [cheshire.core :as json]
            [environ.core :refer [env]]
            [org.httpkit.server :as server]
            [clojure.java.jdbc :as jdbc]
            ;; --- NOVOS REQUIRES PARA OS CANAIS E PROTOCOLO ---
            [sms-notifier.protocols :as p]
            [sms-notifier.channels.sms :as sms]
            [sms-notifier.channels.email :as email]
            [sms-notifier.channels.whatsapp :as whatsapp])
  (:gen-class))

;; --- ESTADO GLOBAL ---
(def sent-notifications-cache (atom #{}))
(def mock-customer-data (atom {}))
(def db-spec (env :database-url))
(def notification-channels (atom [])) ; Atom para manter os canais ativos

;; --- LÓGICA DO CIRCUIT BREAKER (Inalterada) ---
(def circuit-breaker-config {:failure-threshold 3, :reset-timeout-ms 60000})
(def circuit-breaker-state (atom {:state :closed, :failures 0, :last-failure-time nil}))

(defn trip-circuit-breaker! []
  (let [now (System/currentTimeMillis)
        failures (inc (:failures @circuit-breaker-state))]
    (if (>= failures (:failure-threshold circuit-breaker-config))
      (do
        (println "!!! CIRCUIT BREAKER ABERTO: Muitas falhas de DB. Pausando operações com o banco por 1 minuto. !!!")
        (reset! circuit-breaker-state {:state :open, :failures failures, :last-failure-time now}))
      (swap! circuit-breaker-state assoc :failures failures :last-failure-time now))))

(defn reset-circuit-breaker! []
  (when (not= :closed (:state @circuit-breaker-state))
    (println "--- CIRCUIT BREAKER FECHADO: Conexão com o DB restabelecida. ---")
    (reset! circuit-breaker-state {:state :closed, :failures 0, :last-failure-time nil})))

(defmacro with-db-circuit-breaker [db-op-name & body]
  `(let [state# (:state @circuit-breaker-state)]
     (if (and (= :open state#)
              (< (- (System/currentTimeMillis) (:last-failure-time @circuit-breaker-state))
                 (:reset-timeout-ms circuit-breaker-config)))
       (do
         (println (str "Operação de DB '" ~db-op-name "' ignorada. Circuito está aberto."))
         (throw (Exception. (str "Circuit Breaker is open for " ~db-op-name))))
       (try
         (let [result# (do ~@body)]
           (reset-circuit-breaker!)
           result#)
         (catch Exception e#
           (println (str "Falha na operação de DB '" ~db-op-name "'. Acionando Circuit Breaker..."))
           (trip-circuit-breaker!)
           (throw e#))))))

;; --- FUNÇÕES HELPER (DB E CONFIG) ---
(defn load-keys-from-db! []
  (if-not db-spec
    (println "ALERTA: DATABASE_URL não configurada. Cache de idempotência iniciará vazio.")
    (try
      (with-db-circuit-breaker "load-keys"
        (println "Carregando chaves do DB para o cache...")
        (let [keys (jdbc/query db-spec ["SELECT notification_key FROM sent_notifications"])]
          (if (seq keys)
            (let [key-set (->> keys (map :notification_key) (into #{}))]
              (reset! sent-notifications-cache key-set)
              (println (str "Sucesso! " (count key-set) " chaves carregadas.")))
            (println "Nenhuma chave encontrada no DB."))))
      (catch Exception e
        (println (str "ERRO CRÍTICO AO CARREGAR CACHE DO DB: " (.getMessage e)))))))

(defn save-notification-key! [db-conn key]
  (jdbc/insert! db-conn :sent_notifications {:notification_key key})
  (swap! sent-notifications-cache conj key)
  (println (str "Chave " key " salva no DB e no cache.")))

(defn parse-customer-data []
  (if-let [customer-data-json (env :mock-customer-data)]
    (try
      (reset! mock-customer-data (json/parse-string customer-data-json true))
      (println "Dados de clientes mockados carregados.")
      (catch Exception e
        (println (str "Erro ao parsear MOCK_CUSTOMER_DATA: " (.getMessage e)))))
    (println "Aviso: MOCK_CUSTOMER_DATA não definida.")))

(defn get-contact-info [waba-id]
  (get @mock-customer-data (keyword waba-id)))

;; --- LÓGICA PRINCIPAL REESTRUTURADA ---
(defn process-notification [template]
  (let [waba-id (:wabaId template)
        template-id (:id template)
        new-category (:category template)
        notification-key (str template-id "_" new-category)]
    (if (@sent-notifications-cache notification-key)
      (println (str "Notificação para " notification-key " já processada. Ignorando."))
      (if-let [contact-info (get-contact-info waba-id)]
        (do
          (let [message-details {:template-id template-id
                                 :subject (str "Alerta de Mudança de Categoria: " (:elementName template))
                                 :body (str "Alerta de Mudança de Categoria de Template!\n\n"
                                            "CANAL ATIVO: " waba-id "\n\n"
                                            "    Nome do Template: " (:elementName template) "\n"
                                            "    Categoria Anterior: " (:oldCategory template) "\n"
                                            "    Nova Categoria: " new-category "\n\n\n"
                                            "Atenciosamente,\n\n"
                                            "JM MASTER GROUP.")}]
            (println (str "Despachando notificações para " notification-key " via " (count @notification-channels) " canais..."))
            (doseq [channel @notification-channels]
              (try
                (let [response (p/send! channel contact-info message-details)]
                  (if (and response (>= (:status response) 200) (< (:status response) 300)))
                    (println (str "  -> Sucesso no envio via " (type channel)))
                    (println (str "  -> Falha no envio via " (type channel) ". Resposta: " (:body response)))))
                (catch Exception e
                  (println (str "  -> ERRO INESPERADO no canal " (type channel) ": " (.getMessage e)))))))
          (try
            (with-db-circuit-breaker "save-key"
              (jdbc/with-db-connection [db-conn db-spec]
                (save-notification-key! db-conn notification-key)))
            (catch Exception e
              (println (str "Falha ao salvar chave no DB: " (.getMessage e)))))))
        (println (str "Aviso: Informações de contato não encontradas para o WABA ID: " waba-id))))))

(defn fetch-and-process-templates []
  (if-let [watcher-url (env :watcher-url)]
    ;; then (if watcher-url is not nil)
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
    ;; else (if watcher-url is nil)
    (println "ALERTA: WATCHER_URL não configurada.")))

(defn start-notifier-loop! []
  (println "Iniciando o loop do Notifier...")
  (future
    (Thread/sleep 30000)
    (loop []
      (fetch-and-process-templates)
      (Thread/sleep 240000)
      (recur))))

(defn app-handler [request]
  {:status 200, :headers {"Content-Type" "text/plain; charset=utf-8"}, :body "Serviço Notifier (Multi-Channel) está no ar."})

;; --- PONTO DE ENTRADA ATUALIZADO ---
(defn -main [& args]
  (let [port (Integer/parseInt (env :port "8080"))]
    (println "==================================================")
    (println "  SMS Notifier v0.5.0 (Multi-Channel, Refatorado)")
    (println "==================================================")
    (load-keys-from-db!)
    (parse-customer-data)
    (println "Inicializando canais de notificação...")
    (swap! notification-channels conj (sms/make-sms-channel))
    (swap! notification-channels conj (email/make-email-channel))
    (swap! notification-channels conj (whatsapp/make-whatsapp-channel))
    (println (str "Canais ativos (" (count @notification-channels) "): " (mapv type @notification-channels)))
    (if (and (some? db-spec) (empty? @sent-notifications-cache))
      (do
        (println "\n!! ERRO CRÍTICO DE SEGURANÇA: O CACHE DE NOTIFICAÇÕES ESTÁ VAZIO. !!")
        (println "!! PARA PREVENIR O REENVIO EM MASSA, O WORKER NÃO SERÁ INICIADO. !!\n")
        (server/run-server (fn [req] {:status 503 :body "Serviço em modo de segurança."}) {:port port}))
      (do
        (start-notifier-loop!)
        (server/run-server app-handler {:port port})
        (println (str "Servidor web iniciado na porta " port ". Worker ativo."))))))
