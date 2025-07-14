# SMS Notifier

## Visão Geral

O `sms-notifier` é um microsserviço em Clojure que faz parte do **Sistema de Notificação de Mudança de Categoria de Templates (SNCT)**.

Seu propósito é validar o fluxo de notificação de ponta a ponta. Ele opera da seguinte maneira:

1.  **Consome dados** do serviço `notification-watcher`, que detecta mudanças de categoria em templates de mensagens.
2.  **Busca informações de contato** de clientes a partir de uma fonte de dados mockada (variável de ambiente).
3.  **Simula o envio de notificações** por SMS, imprimindo os detalhes da notificação no console.
4.  Garante a **idempotência**, ou seja, que a mesma notificação não seja processada repetidamente, usando um cache em memória.

Esta versão inicial **não utiliza um banco de dados**. Todo o seu estado (contatos e cache de notificações enviadas) é gerenciado em memória.

## Pré-requisitos

*   Java (JDK 11 ou superior)
*   [Leiningen](https://leiningen.org/)
*   O serviço `notification-watcher` deve estar em execução.

## Configuração

O serviço é configurado através de variáveis de ambiente. Você pode exportá-las no seu terminal ou criar um arquivo `.env` na raiz do projeto e usar a biblioteca `environ`.

### Variáveis de Ambiente

*   `WATCHER_URL`
    *   **Descrição:** A URL base onde o serviço `notification-watcher` está escutando.
    *   **Obrigatório:** Não
    *   **Padrão:** `http://localhost:8080`
    *   **Exemplo:** `WATCHER_URL="http://127.0.0.1:8080"`

*   `MOCK_CUSTOMER_DATA`
    *   **Descrição:** Uma string contendo um objeto JSON que mapeia WABA IDs (WhatsApp Business Account IDs) para números de telefone de contato.
    *   **Obrigatório:** Sim (para que as notificações sejam "enviadas")
    *   **Formato:** String JSON com chaves sendo os WABA IDs e valores sendo os números de telefone.
    *   **Exemplo:**
        ```bash
        MOCK_CUSTOMER_DATA='{"waba_id_1": "+5511999998888", "waba_id_2": "+5521888887777"}'
        ```

## Como Executar Localmente

Para executar o sistema completo, você precisará de dois terminais: um para o `notification-watcher` e outro para o `sms-notifier`.

### Terminal 1: Executar o `notification-watcher`

1.  **Navegue até o diretório** do `notification-watcher`:
    ```sh
    cd ../notification-watcher
    ```

2.  **Configure as variáveis de ambiente**. Para testes, é recomendado usar o modo mock da Gupshup. Crie ou edite seu arquivo `.env` ou exporte as seguintes variáveis:
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
    O serviço irá iniciar na porta `8080`.

### Terminal 2: Executar o `sms-notifier`

1.  **Navegue até o diretório** deste projeto:
    ```sh
    cd ../sms-notifier
    ```

2.  **Configure as variáveis de ambiente**. Crie ou edite seu arquivo `.env` ou exporte as seguintes variáveis:
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

*   No terminal do `sms-notifier`, você verá logs indicando que ele está consultando o `notification-watcher`.
*   Quando o `notification-watcher` tiver dados de templates alterados, o `sms-notifier` irá recebê-los.
*   Você verá no console do `sms-notifier` uma saída formatada que **simula o envio de um SMS**, mais ou menos assim:

    ```
    --------------------------------------------------
    SIMULANDO ENVIO DE SMS
    PARA: +5511999998888
    MENSAGEM: Alerta de Mudança de Categoria de Template!
      - WABA ID: waba_id_1
      - Template: meu_template_de_teste (template_123)
      - Categoria Anterior: MARKETING
      - Nova Categoria: UTILITY
    --------------------------------------------------
    ```
*   Nas consultas seguintes, se a mesma notificação for detectada, você verá uma mensagem indicando que ela já foi processada e será ignorada, validando a lógica de idempotência.

---

## Deploy no Render com Docker

Para fazer o deploy deste serviço no [Render](https://render.com/) no plano gratuito, ele deve ser configurado como um **Web Service**.

O serviço foi adaptado para iniciar um servidor web mínimo para satisfazer os requisitos do Render, enquanto o processo principal de notificação continua rodando em background.

### Instruções de Configuração

1.  **Conecte seu repositório** Git ao Render.
2.  Crie um novo **Web Service**.
3.  Durante a criação, o Render irá detectar o `Dockerfile` no seu repositório. Selecione esta opção para o deploy. Os comandos de build e de início já estão definidos dentro do `Dockerfile`.

4.  **Porta (Port)**:
    *   O `Dockerfile` expõe a porta `8080`. O Render detectará isso automaticamente. Certifique-se de que a configuração de porta no Render esteja definida como `8080`.

5.  **Adicione as Variáveis de Ambiente** na aba "Environment" do seu serviço no Render. Estas irão sobrescrever os valores `ENV` definidos no `Dockerfile`.
    *   `WATCHER_URL`: Aponte para a URL pública do seu serviço `notification-watcher` no Render (ex: `https://notification-watcher.onrender.com`).
    *   `MOCK_CUSTOMER_DATA`: Cole a sua string JSON de contatos mockados. Ex: `'{"waba_id_1": "+5511999998888"}'`.
    *   `PORT`:
        *   **Valor:** `8080`

Após salvar, o Render irá construir a imagem Docker, implantar e iniciar o serviço. A URL pública dele mostrará uma mensagem de status, e o worker de notificação estará ativo, com os logs visíveis na aba "Logs".
