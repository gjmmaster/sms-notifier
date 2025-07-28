;; src/sms_notifier/channels/whatsapp.clj
(ns sms-notifier.channels.whatsapp
  (:require [sms-notifier.protocols :as p]
            [environ.core :refer [env]]))

(defrecord WhatsAppChannel []
  p/NotificationChannel
  (send! [this contact-info message-details]
    (let [whatsapp-number (:whatsapp-number contact-info)]
      (if whatsapp-number
        (do
          (println (str "  [WhatsApp Channel] Simulação de envio para: " whatsapp-number))
          ;; TODO: Implementar a lógica real de envio via WhatsApp.
          {:status 200 :body "Simulação de envio de WhatsApp bem-sucedida."})
        {:status 400 :body "Número de WhatsApp não fornecido."}))))

(defn make-whatsapp-channel []
  (->WhatsAppChannel))
