(defproject sms-notifier "0.2.0-SNAPSHOT" ; Versão atualizada para refletir a mudança
  :description "SMS Notifier service with database persistence"
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.11.1"]
                 [clj-http "3.12.3"]
                 [cheshire "5.11.0"]
                 [environ "1.2.0"]
                 [http-kit "2.5.3"]
                 ;; --- DEPENDÊNCIAS NOVAS ADICIONADAS AQUI ---
                 [org.clojure/java.jdbc "0.7.12"]
                 [org.postgresql/postgresql "42.6.0"]]
  :main ^:skip-aot sms-notifier.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all
                       :jvm-opts ["-Dclojure.compiler.direct-linking=true"]}
             :dev {:dependencies [[clj-http-fake "1.0.4"]]
                   :source-paths ["src" "test"]}})
