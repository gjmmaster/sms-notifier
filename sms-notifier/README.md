# SMS Notifier

## Visão Geral

O `sms-notifier` é um microsserviço em Clojure que faz parte do **Sistema de Notificação de Mudança de Categoria de Templates (SNCT)**.

Sua função é consumir alertas sobre mudanças de categoria de templates do serviço `notification-watcher`, identificar o cliente afetado e enviar uma notificação por SMS. Esta versão do serviço é robusta e pronta para produção, utilizando um **banco de dados para garantir a idempotência** e a resiliência do processo de notificação.

## Principais Funcionalidades

  * **Consumo de Alertas**: Periodicamente, o serviço consulta o endpoint `/changed-templates` do `notification-watcher` para buscar novas mudanças.
  * **Envio de Notificações por SMS**: Integra-se com uma API de SMS externa para enviar alertas formatados aos contatos dos clientes.
  * **Idempotência com Persistência**: Utiliza um banco de dados (como PostgreSQL) para registrar todas as notificações já enviadas. Isso previne o envio de alertas duplicados, mesmo que o serviço seja reiniciado.
  * **Resiliência (Circuit Breaker)**: Implementa um padrão de Circuit Breaker que interrompe as operações com o banco de dados em caso de falhas repetidas, evitando sobrecarga e permitindo a recuperação do sistema.
  * **Configuração Flexível**: O comportamento do serviço é totalmente controlado por variáveis de ambiente, facilitando o deploy em diferentes ambientes.

## Documentação Detalhada

Para uma análise aprofundada da arquitetura, fluxo de dados, stack tecnológica e detalhes de implementação, consulte o documento principal do projeto:

  * **[Relatório Técnico Detalhado](https://www.google.com/search?q=./doc/prototipo/relatorio_tecnico_de_todo_prototipo)**

## Como Executar Localmente

Para executar o sistema em seu ambiente local, você precisará do `notification-watcher` e do `sms-notifier` rodando simultaneamente.

### Pré-requisitos

  * Java (JDK 11+)
  * Leiningen
  * Acesso a uma instância de banco de dados PostgreSQL.

### Passo 1: Preparar o Banco de Dados

Conecte-se ao seu banco de dados e execute o seguinte comando SQL para criar a tabela necessária para o controle de idempotência:

```sql
CREATE TABLE IF NOT EXISTS sent_notifications (
    id SERIAL PRIMARY KEY,
    notification_key VARCHAR(255) UNIQUE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
```

### Passo 2: Configurar e Executar o `notification-watcher`

Abra um terminal e siga os passos para o serviço de dependência.

1.  **Navegue até o diretório** do `notification-watcher`.
2.  **Configure as variáveis de ambiente**. Para testes, é recomendado usar o modo mock:
    ```sh
    export GUPSHUP_MOCK_MODE="true"
    export MOCK_CUSTOMER_MANAGER_WABA_IDS="waba_id_1,waba_id_2"
    export PORT="8081" # Use uma porta diferente do sms-notifier
    ```
3.  **Execute o serviço**:
    ```sh
    lein run
    ```

### Passo 3: Configurar e Executar o `sms-notifier`

Abra um segundo terminal.

1.  **Navegue até o diretório** deste projeto (`sms-notifier`).
2.  **Exporte as variáveis de ambiente obrigatórias**:
    ```sh
    # URL para encontrar o serviço notification-watcher
    export WATCHER_URL="http://localhost:8081"

    # String de conexão JDBC para o seu banco de dados
    export DATABASE_URL="jdbc:postgresql://localhost:5432/sms_notifier_db?user=admin&password=secret"

    # Credenciais para a API de envio de SMS
    export SMS_API_URL="https://api.sms_provider.com/send"
    export SMS_API_TOKEN="seu_token_de_api"
    export SMS_API_USER="seu_usuario_de_api"

    # Dados de contato para mapear wabaId -> telefone
    export MOCK_CUSTOMER_DATA='{"waba_id_1": "+5511999998888", "waba_id_2": "+5521888887777"}'

    # Porta onde o serviço sms-notifier irá rodar
    export PORT="8080"
    ```
3.  **Execute o serviço**:
    ```sh
    lein run
    ```

Você verá os logs no console do `sms-notifier` indicando as consultas ao `notification-watcher`, o envio de SMS e o salvamento das chaves de notificação no banco de dados.

## Deploy

O serviço inclui um `Dockerfile` para facilitar o deploy em contêineres. Para instruções detalhadas sobre como fazer o deploy na plataforma [Render](https://render.com/), consulte nosso guia:

  * **[Guia de Deploy no Render (`DEPLOY.md`)](https://www.google.com/search?q=./doc/DEPLOY.md)**
