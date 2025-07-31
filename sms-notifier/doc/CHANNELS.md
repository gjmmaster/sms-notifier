# Visão Geral dos Canais de Notificação

Este documento fornece um resumo de todos os canais de notificação disponíveis no serviço, seu estado atual, e a configuração necessária para cada um.

---

## 1. SMS

*   **Estado:** **Ativo por padrão.**
*   **Descrição:** Envia notificações como mensagens de texto via uma API externa.
*   **Dependência em `MOCK_CUSTOMER_DATA`:** O objeto de contato deve conter a chave `:phone`.
    *   *Exemplo:* `{"phone": "+5511999998888", ...}`
*   **Variáveis de Ambiente Necessárias:**
    *   `SMS_API_URL`
    *   `SMS_API_TOKEN`
    *   `SMS_API_USER`

---

## 2. Email

*   **Estado:** **Ativo por padrão.**
*   **Descrição:** Envia notificações como emails (suporta corpo em HTML) através de uma API externa.
*   **Dependência em `MOCK_CUSTOMER_DATA`:** O objeto de contato deve conter a chave `:email`. Também utiliza a chave `:name` para o destinatário.
    *   *Exemplo:* `{"name": "Nome do Cliente", "email": "cliente@example.com", ...}`
*   **Variáveis de Ambiente Necessárias:**
    *   `EMAIL_API_URL` (Opcional, com valor padrão)
    *   `EMAIL_API_TOKEN`
    *   `EMAIL_API_USER`

---

## 3. WhatsApp

*   **Estado:** **Inativo por padrão e implementação MOCK.**
*   **Descrição:** A implementação atual **não envia mensagens reais**. Ela apenas simula o envio, imprimindo uma mensagem no log do console. Para ativá-lo, é necessário adicioná-lo à lista `active-channels` em `sms-notifier/src/sms_notifier/core.clj` e implementar a lógica de envio real.
*   **Dependência em `MOCK_CUSTOMER_DATA`:** O objeto de contato deve conter a chave `:whatsapp-number`.
    *   *Exemplo:* `{"whatsapp-number": "+5511999998888", ...}`
*   **Variáveis de Ambiente Necessárias:**
    *   Nenhuma no momento. A implementação real provavelmente exigirá novas variáveis.
