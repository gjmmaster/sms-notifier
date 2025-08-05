(ns sms-notifier.scripts.backfill-templates
  "Este script de linha de comando foi projetado para um propósito específico:
   buscar todos os templates atualmente disponíveis no 'notification-watcher'
   e inserir suas chaves de notificação no banco de dados.

   O objetivo é criar um 'ponto de corte', garantindo que o serviço principal
   não processe e envie notificações para templates que já eram considerados
   'alterados' antes deste ponto. Isso efetivamente 'limpa' a fila de
   notificações antigas."
  (:require [clj-http.client :as client]
            [cheshire.core :as json]
            [environ.core :refer [env]]
            [clojure.java.jdbc :as jdbc])
  (:gen-class))

(defn- fetch-all-templates
  "Busca todos os templates do serviço watcher."
  [watcher-url]
  (println (str "Buscando templates do watcher em: " watcher-url))
  (try
    (let [response (client/get (str watcher-url "/changed-templates")
                               {:as :json :throw-exceptions true :conn-timeout 10000 :socket-timeout 10000})
          templates (:body response)]
      (if (and (seq templates) (vector? templates))
        (do
          (println (str "Sucesso! " (count templates) " templates recebidos."))
          templates)
        (do
          (println "Nenhum template encontrado ou a resposta não é um array. Encerrando.")
          [])))
    (catch Exception e
      (println (str "\nERRO CRÍTICO ao conectar-se com o notification-watcher."))
      (println (str "Detalhe: " (.getMessage e)))
      (println "\nPor favor, verifique se a URL do watcher está correta e se o serviço está no ar.")
      nil)))

(defn- generate-notification-key
  "Gera a chave de idempotência para um template, seguindo a mesma lógica do core."
  [template]
  (when (and (:id template) (:category template))
    (str (:id template) "_" (:category template))))

(defn- insert-keys-into-db!
  "Insere uma lista de chaves de notificação no banco de dados.
   Utiliza 'ON CONFLICT DO NOTHING' para evitar erros caso uma chave já exista."
  [db-spec keys-to-insert]
  (if (empty? keys-to-insert)
    (println "Nenhuma chave nova para inserir.")
    (try
      (println (str "Inserindo " (count keys-to-insert) " chaves no banco de dados..."))
      (jdbc/with-db-connection [db-conn db-spec]
        (let [sql "INSERT INTO sent_notifications (notification_key) VALUES (?) ON CONFLICT (notification_key) DO NOTHING"
              ;; O execute! retorna o número de linhas afetadas para cada statement
              results (jdbc/execute! db-conn (into [sql] (map (fn [k] [k]) keys-to-insert)))]
          (let [rows-inserted (apply + results)]
            (println "--------------------------------------------------")
            (println "          Resultado da Operação")
            (println "--------------------------------------------------")
            (println (str "  Total de chaves para inserir : " (count keys-to-insert)))
            (println (str "  Novas chaves inseridas       : " rows-inserted))
            (println "--------------------------------------------------")
            (println "Operação concluída com sucesso!"))))
      (catch Exception e
        (println (str "\nERRO CRÍTICO ao inserir chaves no banco de dados."))
        (println (str "Detalhe: " (.getMessage e)))
        (println "\nPor favor, verifique sua string de conexão DATABASE_URL e as permissões do usuário.")))))

(defn -main [& args]
  (println "==================================================")
  (println "   Script para Baseline de Templates Notificados")
  (println "==================================================")
  (let [watcher-url (env :watcher-url)
        db-spec     (env :database-url)]

    (if (or (nil? watcher-url) (nil? db-spec))
      (println "\nERRO: As variáveis de ambiente WATCHER_URL e DATABASE_URL são obrigatórias.")
      (when-let [templates (fetch-all-templates watcher-url)]
        (let [keys (->> templates
                        (map generate-notification-key)
                        (filter some?) ; Remove nils caso algum template venha malformado
                        (into []))]
          (insert-keys-into-db! db-spec keys))))))
