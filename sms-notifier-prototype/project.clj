(defproject sms-notifier-prototype "0.1.0-SNAPSHOT"
  :description "Prototype for the SMS Notifier service"
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.11.1"]
                 [clj-http "3.12.3"]
                 [cheshire "5.11.0"]
                 [environ "1.2.0"]
                 [http-kit "2.5.3"]]
  :main ^:skip-aot sms-notifier-prototype.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all
                       :jvm-opts ["-Dclojure.compiler.direct-linking=true"]}})
