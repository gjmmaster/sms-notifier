(ns sms-notifier.utils)

(defn clean-template-name
  "Remove o hash de identificação do final do nome de um template."
  [element-name]
  (clojure.string/replace element-name #"_[0-9a-f]{16,}" ""))
