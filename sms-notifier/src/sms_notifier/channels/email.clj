;; src/sms_notifier/channels/email.clj
(ns sms-notifier.channels.email
  (:require [sms-notifier.protocols :as p]
            [environ.core :refer [env]]))
            ;; Para a implementação real, será necessário: [postal.core :as postal]

(defrecord EmailChannel []
  p/NotificationChannel
  (send! [this contact-info message-details]
    (let [email-address (:email contact-info)]
      (if email-address
        (do
          (println (str "  [Email Channel] Simulação de envio para: " email-address))
          ;; TODO: Implementar a lógica real de envio de email com Postal.
          {:status 200 :body "Simulação de envio de email bem-sucedida."})
        {:status 400 :body "Endereço de email não fornecido."}))))

(defn make-email-channel []
  (->EmailChannel))
