(ns sms-notifier.core
  (:require [clj-http.client :as client]
            [cheshire.core :as json]
            [environ.core :refer [env]]
            [org.httpkit.server :as server]
            [clojure.java.jdbc :as jdbc]
            [sms-notifier.protocols :as p]
            [sms-notifier.channels.sms :as sms]
            [sms-notifier.channels.email :as email])
  (:gen-class))

(def sent-notifications-cache (atom #{}))
(def mock-customer-data (atom {}))
(def db-spec (env :database-url))
(def active-channels [(sms/make-sms-channel) (email/make-email-channel)])

;; --- LÓGICA DO CIRCUIT BREAKER PARA O BANCO DE DADOS ---
(def circuit-breaker-config {:failure-threshold 3, :reset-timeout-ms 60000}) ; 3 falhas, 1 min de reset
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

;; --- FUNÇÕES HELPER (DEFINIDAS ANTES DE SEREM USADAS) ---

(defn load-keys-from-db! []
  (if-not db-spec
    (println "ALERTA: DATABASE_URL não configurada. Cache de idempotência iniciará vazio.")
    (try
      (with-db-circuit-breaker "load-keys"
        (println "Carregando chaves de notificações do banco de dados para o cache em memória...")
        (let [keys (jdbc/query db-spec ["SELECT notification_key FROM sent_notifications"])]
          (if (seq keys)
            (let [key-set (->> keys (map :notification_key) (into #{}))]
              (reset! sent-notifications-cache key-set)
              (println (str "Sucesso! " (count key-set) " chaves carregadas para o cache.")))
            (println "Nenhuma chave encontrada no banco de dados. O cache iniciará vazio."))))
      (catch Exception e
        (println (str "ERRO CRÍTICO AO CARREGAR CACHE DO DB: " (.getMessage e)))))))

(defn save-notification-key! [db-conn key]
  (jdbc/insert! db-conn :sent_notifications {:notification_key key})
  (swap! sent-notifications-cache conj key)
  (println (str "Chave " key " salva no DB e no cache em memória.")))

(defn parse-customer-data []
  (if-let [customer-data-json (env :mock-customer-data)]
    (try
      (let [parsed-data (json/parse-string customer-data-json true)]
        (reset! mock-customer-data parsed-data)
        (println "Dados de clientes mockados carregados."))
      (catch Exception e
        (println (str "Erro ao parsear MOCK_CUSTOMER_DATA: " (.getMessage e)))))
    (println "Aviso: MOCK_CUSTOMER_DATA não definida.")))

(defn get-contact-info [waba-id]
  (get @mock-customer-data (keyword waba-id)))

;; --- LÓGICA PRINCIPAL ---

(defn- build-message-details [template]
  {:sms   (str "Alerta de Mudança de Categoria de Template!\n\n"
               "CANAL ATIVO: " (:wabaId template) "\n\n"
               "    Nome do Template: " (:elementName template) "\n"
               "    Categoria Anterior: " (:oldCategory template) "\n"
               "    Nova Categoria: " (:category template) "\n\n\n"
               "Atenciosamente,\n\n"
               "JM MASTER GROUP.")
   :email {:subject (str "Alerta de Mudança de Categoria de Template: " (:elementName template))
           :body    (str "<h1>Alerta de Mudança de Categoria!</h1>"
                         "<p><b>CANAL ATIVO:</b> " (:wabaId template) "</p>"
                         "<ul>"
                         "<li><b>Nome do Template:</b> " (:elementName template) "</li>"
                         "<li><b>Categoria Anterior:</b> " (:oldCategory template) "</li>"
                         "<li><b>Nova Categoria:</b> " (:category template) "</li>"
                         "</ul>"
                         "<p>Atenciosamente,<br>JM MASTER GROUP.</p>")}
   :template-id (:id template)})

(def SPAM_LIMIT 5)

(defn- persist-notification-key! [notification-key]
  (try
    (with-db-circuit-breaker "save-key"
      (jdbc/with-db-connection [db-conn db-spec]
        (save-notification-key! db-conn notification-key)))
    (catch Exception e
      (println (str "Falha ao salvar chave no DB (Circuito Aberto?): " (.getMessage e))))))

(defn process-notification [template spam-counter]
  (let [waba-id          (:wabaId template)
        template-id      (:id template)
        new-category     (:category template)
        notification-key (str template-id "_" new-category)]
    (if (@sent-notifications-cache notification-key)
      (println (str "Notificação para " notification-key " já processada (cache). Ignorando."))
      (if-let [raw-contact-info (get-contact-info waba-id)]
        (let [contact-list (if (sequential? raw-contact-info)
                             raw-contact-info
                             [raw-contact-info])]
          (doseq [contact-info contact-list]
            (let [current-count (get @spam-counter contact-info 0)]
              (if (>= current-count SPAM_LIMIT)
                (println (str "ALERTA DE SPAM: Limite de " SPAM_LIMIT " mensagens para " contact-info " atingido. Notificação para " notification-key " bloqueada."))
                (do
                  (swap! spam-counter update contact-info (fn [c] (inc (or c 0))))
                  (let [message-details (build-message-details template)
                        sms-message   {:body (:sms message-details) :template-id (:template-id message-details)}
                        email-message {:subject (:subject (:email message-details)) :body (:body (:email message-details)) :template-id (:template-id message-details)}]
                    (doseq [channel active-channels]
                      (try
                        (let [response (p/send! channel contact-info (if (= (type channel) sms_notifier.channels.sms.SmsChannel) sms-message email-message))]
                          (if (= (:status response) 200)
                            (println (str "Notificação enviada com sucesso para " (:name contact-info) " via " (type channel) "."))
                            (println (str "Falha ao enviar notificação para " (:name contact-info) " via " (type channel) ". Resposta: " (:body response)))))
                        (catch Exception e
                          (println (str "Erro ao enviar notificação para " (:name contact-info) " via " (type channel) ": " (.getMessage e)))))))))))
          (persist-notification-key! notification-key))
        (println (str "Aviso: Contato não encontrado para o WABA ID: " waba-id))))))

(defn fetch-and-process-templates []
  (if-let [watcher-url (env :watcher-url)]
    (try
      (println (str "Consultando " watcher-url "/changed-templates..."))
      (let [response (client/get (str watcher-url "/changed-templates") {:as :json, :throw-exceptions false, :conn-timeout 5000, :socket-timeout 5000})
            templates (get-in response [:body])
            spam-counter (atom {})]
        (if (and (= (:status response) 200) (seq templates))
          (do
            (println (str "Recebidos " (count templates) " templates alterados."))
            (doseq [template templates] (process-notification template spam-counter)))
          (println "Nenhum template alterado encontrado.")))
      (catch Exception e
        (println (str "Erro ao conectar com o notification-watcher: " (.getMessage e)))))
    (println "ALERTA: WATCHER_URL não configurada.")))

(defn start-notifier-loop! []
  (println "Iniciando o loop do SMS Notifier...")
  (future
    (Thread/sleep 30000)
    (loop []
      (fetch-and-process-templates)
      (Thread/sleep 240000)
      (recur))))

(defn app-handler [request]
  {:status 200, :headers {"Content-Type" "text/plain; charset=utf-8"}, :body "Serviço SMS Notifier está no ar."})

;; --- PONTO DE ENTRADA ATUALIZADO COM VERIFICAÇÃO DE SEGURANÇA ---
(defn -main [& args]
  (let [port (Integer/parseInt (env :port "8080"))]
    (println "======================================")
    (println "  SMS Notifier v0.4.1 (Resiliente, Ordem Corrigida)")
    (println "======================================")

    (load-keys-from-db!)
    (parse-customer-data)

    (if (and (some? db-spec) (empty? @sent-notifications-cache))
      (do
        (println "\n!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!")
        (println "!! ERRO CRÍTICO DE SEGURANÇA: O CACHE DE NOTIFICAÇÕES ESTÁ VAZIO. !!")
        (println "!! PARA PREVENIR O REENVIO EM MASSA DE SMS, O WORKER NÃO SERÁ INICIADO. !!")
        (println "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!\n")
        (server/run-server (fn [req] {:status 503 :body "Serviço em modo de segurança. Worker inativo."}) {:port port}))
      (do
        (start-notifier-loop!)
        (server/run-server app-handler {:port port})
        (println (str "Servidor web iniciado na porta " port ". Worker ativo."))))))
