;; src/sms_notifier/channels/whatsapp.clj
(ns sms-notifier.channels.whatsapp
  (:require [sms-notifier.protocols :as p]
            [environ.core :refer [env]]))

(defrecord WhatsAppChannel []
  p/NotificationChannel
  (send! [this contact-info message-details]
    (let [whatsapp-numbers (let [w (:whatsapp-number contact-info)]
                             (cond
                               (coll? w) w
                               (nil? w) []
                               :else [w]))]
      (if (empty? whatsapp-numbers)
        {:status 400 :body "Nenhum número de WhatsApp fornecido."}
        (do
          (doseq [whatsapp-number whatsapp-numbers]
            (println (str "  [WhatsApp Channel] Simulação de envio para: " whatsapp-number)))
          ;; TODO: Implementar a lógica real de envio via WhatsApp.
          {:status 200 :body "Simulação de envio de WhatsApp bem-sucedida para todos os números."})))))

(defn make-whatsapp-channel []
  (->WhatsAppChannel))
