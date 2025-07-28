;; src/sms_notifier/protocols.clj
(ns sms-notifier.protocols
  (:doc "Define os protocolos para os canais de notificação do sistema.
         Qualquer novo canal (ex: Slack, Telegram) deve implementar o protocolo
         NotificationChannel para se integrar ao fluxo de trabalho principal."))

(defprotocol NotificationChannel
  "Define um contrato para um canal de notificação.
   Qualquer canal (SMS, Email, etc.) deve ser capaz de enviar uma notificação."

  (send! [this contact-info message-details]
    "Envia a notificação através de um canal específico.

     Argumentos:
     - 'this': A instância do canal que implementa este protocolo (ex: o record de SMS).
               É passado implicitamente.
     - 'contact-info': Um mapa contendo os dados de contato do destinatário.
                       Cada canal irá extrair a informação que precisa deste mapa.
                       Exemplo: {:phone \"+5511...\", :email \"user@...\", :whatsapp-number \"+5511...\"}
     - 'message-details': Um mapa com os detalhes da mensagem a ser enviada.
                          Exemplo: {:subject \"Assunto do Email\", :body \"Corpo da mensagem\", :template-id \"id-123\"}

     Retorno:
     Deve retornar um mapa indicando o resultado da operação,
     idealmente com uma chave :status (ex: 200 para sucesso) e :body com detalhes.
     Exemplo de sucesso: {:status 200, :body \"SMS enviado com sucesso.\"}
     Exemplo de falha:   {:status 500, :body \"Token da API inválido.\"}"))
