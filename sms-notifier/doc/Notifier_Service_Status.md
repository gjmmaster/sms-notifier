# Documentação Técnica Detalhada: Serviço de Notificação Multicanal

**ID do Documento:** DOC-SN-CORE-20250731-v1.3
**Data:** 31 de Julho de 2025
**Versão:** 1.3 (Reflete o tratamento flexível de contatos)
**Autores:** Jules (AI Assistant), Contribuidores do Projeto

## 1. Propósito

Este documento fornece uma descrição técnica detalhada da implementação atual do microsserviço de notificação, conhecido internamente como `sms-notifier` por razões de legado. O foco é em sua arquitetura multicanal, detalhando os componentes, o fluxo de dados, a configuração e a lógica de negócio implementada. O objetivo é servir como uma referência técnica e guia de onboarding para desenvolvedores.

## 2. Visão Geral do Serviço

O serviço de notificação é um componente em Clojure desenhado para atuar como o ponto final no fluxo de notificação do sistema SNCT. Suas responsabilidades são:

1.  **Consumir Dados de Mudanças:** Periodicamente, consultar o endpoint `/changed-templates` do serviço `notification-watcher` para obter a lista de templates que tiveram suas categorias alteradas.
2.  **Mapear Contatos:** Para cada template alterado, identificar o cliente (via `wabaId`) e buscar suas informações de contato (telefone, email, etc.) em uma fonte de dados configurável.
3.  **Enviar Notificações Multicanal:** Formatar e enviar alertas através de múltiplos canais. **Atualmente, os canais de SMS e Email estão ativos por padrão.**
4.  **Garantir Idempotência com Persistência:** Assegurar que uma notificação para uma determinada mudança seja enviada apenas uma vez, utilizando um banco de dados PostgreSQL para persistir as chaves de cada notificação enviada.
5.  **Operar como Web Service:** Expor um endpoint HTTP (`/`) para compatibilidade com plataformas de deploy como o Render, enquanto o trabalho principal é executado em background.
6.  **Garantir Resiliência:** Utilizar um padrão **Circuit Breaker** para gerenciar a conexão com o banco de dados.

## 3. Arquitetura Detalhada e Componentes

A arquitetura foi refatorada para suportar múltiplos canais de notificação de forma extensível, utilizando um protocolo.

### 3.1. O Protocolo `NotificationChannel`

O coração da nova arquitetura é o protocolo `sms-notifier.protocols/NotificationChannel`. Ele define um contrato `(send! [this contact-info message-details])` que qualquer canal de notificação deve implementar. Isso permite que o sistema adicione novos canais (como WhatsApp, Slack, etc.) com o mínimo de alterações no fluxo principal.

*   `contact-info`: Um mapa contendo todos os dados de contato de um cliente (ex: `{:phone "...", :email "..."}`).
*   `message-details`: Um mapa com o conteúdo da mensagem a ser enviada.

### 3.2. Configuração e Variáveis de Ambiente

O serviço é configurado exclusivamente por variáveis de ambiente.

#### Variáveis Gerais
*   **`WATCHER_URL`** (String): URL base do serviço `notification-watcher`.
*   **`PORT`** (String): Porta TCP para o servidor web integrado. Padrão: `"8080"`.
*   **`DATABASE_URL`** (String): String de conexão JDBC para o banco de dados PostgreSQL. Ex: `"jdbc:postgresql://user:pass@host:port/dbname"`.

#### Variáveis de Contato
*   **`MOCK_CUSTOMER_DATA`** (String): String JSON usada para mapear um `wabaId` aos seus contatos. A estrutura agora é **altamente flexível**:
    *   Um `wabaId` pode mapear para um **único objeto** de contato ou para uma **lista de objetos** de contato.
    *   Dentro de um objeto de contato, os valores para `:phone`, `:email`, e `:whatsapp-number` podem ser uma **única string** ou uma **lista de strings**.
    *   **Exemplo:**
        ```json
        '{
          "waba_id_1": [
            {
              "name": "Empresa A Contato 1",
              "phone": "111111111",
              "email": "contato1@empresa_a.com"
            },
            {
              "name": "Empresa A Contato 2",
              "phone": ["222222222", "333333333"]
            }
          ],
          "waba_id_2": {
            "name": "Empresa B",
            "email": ["financeiro@empresa_b.com", "ceo@empresa_b.com"]
          }
        }'
        ```

#### Variáveis por Canal

*   **Canal de SMS (Ativo por padrão)**
    *   `SMS_API_URL` (String): URL da API do provedor de SMS.
    *   `SMS_API_TOKEN` (String): Token de autenticação.
    *   `SMS_API_USER` (String): Usuário da API.

*   **Canal de Email (Ativo por padrão)**
    *   `EMAIL_API_URL` (String): URL da API de envio de emails. Opcional, possui um valor padrão.
    *   `EMAIL_API_TOKEN` (String): Token de autenticação.
    *   `EMAIL_API_USER` (String): Usuário da API.

*   **Canal de WhatsApp (Inativo por padrão, implementação mock)**
    *   Nenhuma variável de ambiente necessária no momento.

### 3.3. Lógica de Negócio: Fluxo de Processamento de Notificação

*   **`process-notification [template]`**: Contém a lógica central.
    *   **Fluxo de Execução Atualizado:**
        1.  Cria a `notification-key` a partir do template.
        2.  Verifica a existência da chave no cache de idempotência.
        3.  Se a chave é nova, busca os dados de contato para o `wabaId`. O sistema normaliza o resultado para sempre ser uma **lista de contatos**.
        4.  O sistema itera sobre cada **contato** na lista.
        5.  Para cada contato, ele aplica a lógica de anti-spam.
        6.  Se o contato não estiver bloqueado, o sistema itera sobre a lista de `active-channels` (SMS, Email, etc.).
        7.  A função `p/send!` de cada canal é chamada. Os canais são responsáveis por lidar com múltiplos valores (ex: uma lista de emails no campo `:email`).
        8.  **Após todos os contatos terem sido processados**, a `notification-key` é salva uma única vez no banco de dados para garantir a idempotência.

## 4. Considerações para Evolução Futura

1.  **Implementar e Ativar o Canal de WhatsApp:** A implementação atual para WhatsApp é um mock. O próximo passo é integrar com uma API real de WhatsApp e adicioná-lo à lista de canais ativos em `core.clj`.
2.  **Atualização da Suíte de Testes:** **(Prioridade Alta)** Os testes unitários em `core_test.clj` precisam ser atualizados para mockar a nova arquitetura de canais e testar o fluxo multicanal.
3.  **Melhorar a Seleção de Mensagem por Canal:** A lógica atual em `core.clj` para selecionar o corpo da mensagem (SMS vs Email) pode ser refatorada para ser mais robusta e extensível para futuros canais.
4.  **Logging Estruturado:** Substituir `println` por uma biblioteca de logging mais robusta.
5.  **Fonte de Dados de Contato Real**: Substituir `MOCK_CUSTOMER_DATA` por uma chamada de API a um serviço de CRM.
6.  **Escalabilidade Horizontal**: Para rodar múltiplas instâncias, seria necessário implementar um mecanismo de locking distribuído.
