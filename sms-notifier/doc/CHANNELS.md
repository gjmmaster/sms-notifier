# Visão Geral dos Canais de Notificação

Este documento fornece um resumo de todos os canais de notificação disponíveis no serviço, seu estado atual, e a configuração necessária para cada um.

---

## Arquitetura de Canais

A arquitetura do serviço foi refatorada para um modelo baseado em **protocolos**, onde cada canal de notificação é uma implementação independente e auto-contida. Isso torna o sistema mais modular e fácil de estender.

O coração dessa arquitetura é o protocolo `NotificationChannel`, definido em `src/sms_notifier/protocols.clj`. Ele estabelece um contrato único que todos os canais devem seguir, garantindo que o núcleo do sistema possa interagir com qualquer canal de forma agnóstica.

### O Protocolo `NotificationChannel`

Este protocolo define uma única função:

*   `send! [this contact-info message-details]`
    *   **Responsabilidade**: Enviar uma notificação para um contato específico.
    *   **Argumentos**:
        *   `contact-info`: Um mapa com todos os dados de contato disponíveis para um cliente (ex: `{:name "...", :phone "...", :email "..."}`). O canal é responsável por extrair a informação que lhe é relevante.
        *   `message-details`: Um mapa que contém os detalhes brutos do alerta (ex: nome do template, nova categoria, etc.).
    *   **Principal Característica**: Cada implementação de `send!` é responsável por **construir o corpo da mensagem** de acordo com as necessidades do seu canal (seja formatando um SMS, um email em HTML, ou uma mensagem para o WhatsApp).

Essa abordagem descentraliza a lógica de formatação de mensagens, tornando cada canal o único responsável por como suas notificações são apresentadas.

---

## Como Criar um Novo Canal

Para adicionar um novo canal de notificação (ex: Slack, Telegram), siga os passos abaixo.

### Passo 1: Criar o Arquivo do Canal

Crie um novo arquivo `.clj` no diretório `src/sms_notifier/channels/`. O nome do arquivo deve ser representativo do canal (ex: `slack.clj`).

### Passo 2: Implementar o Protocolo `NotificationChannel`

No novo arquivo, defina um `defrecord` que implemente o protocolo `p/NotificationChannel`. Este record irá manter o estado do seu canal, se houver.

```clojure
(ns sms-notifier.channels.slack
  (:require [sms-notifier.protocols :as p]
            [clj-http.client :as client]
            [environ.core :refer [env]]))

(defrecord SlackChannel []
  p/NotificationChannel
  (send! [this contact-info message-details]
    ; Lógica de envio aqui
    ))
```

### Passo 3: Desenvolver a Lógica de Envio

Dentro da função `send!`, você deve:

1.  **Extrair as informações de contato relevantes**: Busque no mapa `contact-info` o dado que seu canal precisa (ex: `:slack-id`).
2.  **Construir o corpo da mensagem**: Use os dados de `message-details` para criar uma mensagem formatada, específica para o seu canal.
3.  **Realizar a chamada à API externa**: Utilize as variáveis de ambiente necessárias para se autenticar e enviar a notificação.
4.  **Retornar um resultado padronizado**: A função deve retornar um mapa com `:status` e `:body`, como `{:status 200 :body "Mensagem enviada"}`.

### Passo 4: Criar uma Função Construtora

Exponha uma função pública que crie uma instância do seu canal.

```clojure
(defn make-slack-channel []
  (->SlackChannel))
```

### Passo 5: Ativar o Canal no Sistema

Finalmente, vá até o arquivo `src/sms_notifier/core.clj` e adicione seu novo canal à lista `active-channels`.

1.  **Adicione o `require` do seu novo namespace**:
    ```clojure
    (ns sms-notifier.core
      (:require ...
                [sms-notifier.channels.slack :as slack]))
    ```
2.  **Instancie e adicione seu canal à lista**:
    ```clojure
    (def active-channels
      [(sms/make-sms-channel)
       (email/make-email-channel)
       (slack/make-slack-channel)]) ; <-- Adicione aqui
    ```

Com isso, seu novo canal estará integrado ao fluxo de notificações do serviço.

---

## 1. SMS

*   **Estado:** **Ativo por padrão.**
*   **Implementação:** `src/sms_notifier/channels/sms.clj`
*   **Descrição:** Envia notificações como mensagens de texto. A formatação da mensagem é definida dentro da função `build-sms-body` no arquivo de implementação.
*   **Dependência em `MOCK_CUSTOMER_DATA`:** O objeto de contato deve conter a chave `:phone`. O valor pode ser uma **string única** ou uma **lista de strings**.
    *   *Exemplo:* `{"name": "...", "phone": ["1111", "2222"], ...}`
*   **Variáveis de Ambiente Necessárias:**
    *   `SMS_API_URL`
    *   `SMS_API_TOKEN`
    *   `SMS_API_USER`

---

## 2. Email

*   **Estado:** **Ativo por padrão.**
*   **Implementação:** `src/sms_notifier/channels/email.clj`
*   **Descrição:** Envia notificações como emails. A formatação do corpo do email (que suporta HTML) é definida dentro da função `build-email-body` no arquivo de implementação.
*   **Dependência em `MOCK_CUSTOMER_DATA`:** O objeto de contato deve conter a chave `:email`. O valor pode ser uma **string única** ou uma **lista de strings**.
    *   *Exemplo:* `{"name": "...", "email": ["email1@a.com", "email2@a.com"], ...}`
*   **Variáveis de Ambiente Necessárias:**
    *   `EMAIL_API_URL` (Opcional, com valor padrão)
    *   `EMAIL_API_TOKEN`
    *   `EMAIL_API_USER`

---

## 3. WhatsApp

*   **Estado:** **Inativo por padrão e implementação MOCK.**
*   **Implementação:** `src/sms_notifier/channels/whatsapp.clj`
*   **Descrição:** A implementação atual **não envia mensagens reais**. Ela apenas simula o envio, imprimindo no console uma mensagem formatada pela função `build-whatsapp-body`. Para ativá-lo, siga o passo 5 do guia "Como Criar um Novo Canal".
*   **Dependência em `MOCK_CUSTOMER_DATA`:** O objeto de contato deve conter a chave `:whatsapp-number`. O valor pode ser uma **string única** ou uma **lista de strings**.
    *   *Exemplo:* `{"name": "...", "whatsapp-number": ["1111", "2222"], ...}`
*   **Variáveis de Ambiente Necessárias:**
    *   Nenhuma no momento. A implementação real provavelmente exigirá novas variáveis.
