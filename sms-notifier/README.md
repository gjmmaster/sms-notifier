# SMS Notifier

## Visão Geral

O `sms-notifier` é um microsserviço em Clojure que faz parte do **Sistema de Notificação de Mudança de Categoria de Templates (SNCT)**.

Seu propósito é consumir informações sobre mudanças de templates do serviço `notification-watcher`, identificar o cliente afetado e enviar uma notificação.

Esta versão inicial do serviço opera com um cache de idempotência em memória e uma fonte de dados de contato mockada (configurada por variáveis de ambiente), e simula o envio de SMS imprimindo no console.

## Documentação Detalhada

Para uma compreensão aprofundada da arquitetura, funcionamento interno, configuração e pontos de evolução do serviço, consulte a seguinte documentação:

*   **[Documentação Técnica Detalhada (`SMS_Notifier_Status.md`)](./doc/SMS_Notifier_Status.md)**

Para instruções sobre como implantar este serviço na plataforma Render, consulte o guia de deploy:

*   **[Guia de Deploy no Render (`DEPLOY.md`)](./doc/DEPLOY.md)**

## Como Executar Localmente

Para executar o sistema completo em seu ambiente local, você precisará de dois terminais: um para o `notification-watcher` e outro para o `sms-notifier`.

### Terminal 1: Executar o `notification-watcher`

1.  **Navegue até o diretório** do `notification-watcher`:
    ```sh
    cd ../notification-watcher
    ```

2.  **Configure as variáveis de ambiente**. Para testes, é recomendado usar o modo mock da Gupshup:
    ```sh
    export GUPSHUP_MOCK_MODE="true"
    export MOCK_CUSTOMER_MANAGER_WABA_IDS="waba_id_1,waba_id_2"
    export PORT="8080"
    ```
    *Nota: Os `MOCK_CUSTOMER_MANAGER_WABA_IDS` devem corresponder às chaves no `MOCK_CUSTOMER_DATA` do `sms-notifier`.*

3.  **Execute o serviço**:
    ```sh
    lein run
    ```

### Terminal 2: Executar o `sms-notifier`

1.  **Navegue até o diretório** deste projeto:
    ```sh
    cd ../sms-notifier
    ```

2.  **Configure as variáveis de ambiente**:
    ```sh
    export WATCHER_URL="http://localhost:8080"
    export MOCK_CUSTOMER_DATA='{"waba_id_1": "+5511999998888", "waba_id_2": "+5521888887777"}'
    ```

3.  **Instale as dependências**:
    ```sh
    lein deps
    ```

4.  **Execute o serviço**:
    ```sh
    lein run
    ```

### O que Esperar

Você verá logs no console do `sms-notifier` indicando as consultas ao `notification-watcher` e a simulação do envio de SMS para os contatos correspondentes.
