# Documentação Técnica Detalhada: Serviço `sms-notifier`

**ID do Documento:** DOC-SN-CORE-20231029-v1.0 (Substituir pela data da efetivação)
**Data:** 29 de Outubro de 2023 (Substituir pela data da efetivação)
**Versão:** 1.0 (Reflete o estado inicial do serviço, adaptado para deploy como Web Service)
**Autores:** Jules (AI Assistant), Contribuidores do Projeto

## 1. Propósito

Este documento fornece uma descrição técnica detalhada da implementação atual do microsserviço `sms-notifier`, com foco no seu arquivo principal `core.clj` (`sms-notifier/src/sms_notifier/core.clj`). O objetivo é servir como uma referência técnica e guia de onboarding para desenvolvedores (humanos ou IA), detalhando a arquitetura dos componentes, fluxos de dados, configuração e a lógica de negócio implementada.

## 2. Visão Geral do Serviço

O `sms-notifier` é um serviço em Clojure desenhado para atuar como o componente final no fluxo de notificação do sistema SNCT. Suas responsabilidades são:

1.  **Consumir Dados de Mudanças:** Periodicamente, consultar o endpoint `/changed-templates` do serviço `notification-watcher` para obter a lista de templates que tiveram suas categorias alteradas.
2.  **Mapear Contatos:** Para cada template alterado, identificar o cliente correspondente (via `wabaId`) e buscar seu número de telefone de contato em uma fonte de dados configurável (atualmente, uma variável de ambiente mockada).
3.  **Garantir Idempotência:** Assegurar que uma notificação para uma mudança específica (mesmo template, mesma nova categoria) seja enviada apenas uma vez. Isso é feito através de um cache em memória.
4.  **Enviar Notificações via API:** Formatar uma mensagem de alerta e enviá-la como um SMS por meio de uma chamada a um provedor de SMS configurável.
5.  **Operar como Web Service:** Expor um endpoint HTTP mínimo (`/`) para ser compatível com plataformas de deploy como o Render (plano gratuito), que exigem que os serviços respondam a requisições HTTP. O trabalho principal do serviço, no entanto, é executado em um processo de background.

## 3. Arquitetura Detalhada e Componentes do `core.clj`

Esta seção detalha os principais componentes e a lógica contida em `sms-notifier/src/sms_notifier/core.clj`.

### 3.1. Configuração e Variáveis de Ambiente

O comportamento do serviço é controlado por variáveis de ambiente, permitindo flexibilidade para diferentes ambientes. A biblioteca `environ` é usada para acessá-las.

*   **`WATCHER_URL`** (String)
    *   **Propósito:** URL base do serviço `notification-watcher`, de onde os dados de templates alterados são consumidos.
    *   **Uso no Código:** Lida pela função `fetch-and-process-templates` para construir a URL completa da API (`<WATCHER_URL>/changed-templates`).
    *   **Obrigatoriedade:** Opcional.
    *   **Padrão:** `"http://localhost:8080"` se não for fornecida.

*   **`MOCK_CUSTOMER_DATA`** (String)
    *   **Propósito:** Fonte de dados mockada para mapear WABA IDs a números de telefone de contato. Essencial para a operação desta versão do protótipo.
    *   **Uso no Código:** Lida pela função `parse-customer-data` na inicialização do serviço. O conteúdo JSON é parseado e armazenado no atom `mock-customer-data`. A função `get-contact-phone` consulta este atom.
    *   **Obrigatoriedade:** Fortemente recomendada. Sem ela, nenhuma notificação será "enviada", pois não haverá como encontrar os números de telefone.
    *   **Formato Exemplo:** `'{"waba_id_1": "+5511999998888", "waba_id_2": "+5521888887777"}'`

*   **`PORT`** (String)
    *   **Propósito:** Especifica a porta TCP na qual o servidor web integrado (http-kit) irá escutar por requisições HTTP.
    *   **Uso no Código:** Lida na função `-main` e passada para `server/run-server`.
    *   **Obrigatoriedade:** Opcional.
    *   **Padrão:** `"8080"` se não for definida.

### 3.2. Estado Global (Atoms)

O serviço utiliza `atom`s do Clojure para gerenciar estado mutável de forma segura e concorrente.

*   **`sent-notifications-cache`** (`clojure.lang.Atom`)
    *   **Definição:** `(atom #{})`
    *   **Propósito:** Armazena um conjunto (`set`) de chaves únicas que representam as notificações que já foram processadas e "enviadas". Este é o mecanismo que garante a **idempotência**.
    *   **Gerenciamento:**
        *   **Atualização:** A função `process-notification` adiciona uma nova chave a este `set` usando `swap!` sempre que uma notificação é enviada com sucesso. A chave é uma string composta pelo ID do template e sua nova categoria (ex: `"template123_MARKETING"`).
        *   **Consumo:** A mesma função `process-notification` verifica se a chave já existe no `set` antes de processar uma nova notificação. Se a chave existir, o processo é abortado para aquele template.

*   **`mock-customer-data`** (`clojure.lang.Atom`)
    *   **Definição:** `(atom {})`
    *   **Propósito:** Armazena o mapa de WABA IDs para números de telefone, carregado da variável de ambiente `MOCK_CUSTOMER_DATA`.
    *   **Gerenciamento:**
        *   **Atualização:** A função `parse-customer-data` usa `reset!` para popular este atom no início da aplicação.
        *   **Consumo:** A função `get-contact-phone` lê (`@`) este atom para buscar o telefone associado a um `wabaId`.

### 3.3. Ponto de Entrada, Servidor Web e Loop Principal

A execução do serviço é orquestrada por três funções principais.

*   **`-main [& args]`**
    *   **Propósito:** Ponto de entrada principal da aplicação (`lein run` ou `java -jar ...`).
    *   **Funcionalidade:**
        1.  Parseia a variável de ambiente `PORT`.
        2.  Imprime um banner de inicialização.
        3.  Chama `parse-customer-data` para carregar os contatos.
        4.  Chama `start-notifier-loop!` para iniciar o processo de polling em uma thread de background.
        5.  Inicia o servidor HTTP `http-kit` na porta configurada, usando `app-handler`. Esta chamada bloqueia a thread principal, mantendo a aplicação viva.

*   **`app-handler [request]`**
    *   **Propósito:** Handler HTTP minimalista para que o serviço se comporte como um Web Service.
    *   **Funcionalidade:** Responde a qualquer requisição com um status `200 OK` e uma mensagem de texto simples, confirmando que o serviço está no ar.

*   **`start-notifier-loop!`**
    *   **Propósito:** Inicia o ciclo de trabalho principal do serviço em uma thread separada para não bloquear o servidor web.
    *   **Funcionalidade:**
        1.  Inicia um processo (`future`).
        2.  Aguarda 30 segundos (um atraso inicial para permitir que outros serviços iniciem).
        3.  Entra em um `loop` infinito:
            *   Chama `fetch-and-process-templates` para executar um ciclo de verificação.
            *   Aguarda 1 minuto (`Thread/sleep 60000`).
            *   Chama a si mesmo (`recur`) para o próximo ciclo.

### 3.4. Lógica de Negócio: Fluxo de Processamento de Notificação

*   **`fetch-and-process-templates []`**
    *   **Propósito:** Orquestra um ciclo completo de busca e processamento.
    *   **Fluxo de Execução:**
        1.  Obtém a URL do `notification-watcher` a partir da variável de ambiente.
        2.  Realiza uma chamada HTTP GET para `.../changed-templates` usando `clj-http.client`.
            *   Configura timeouts (`:conn-timeout`, `:socket-timeout`) para resiliência.
            *   Usa `:as :json` para parsear a resposta automaticamente.
            *   Usa `:throw-exceptions false` para tratar erros de status HTTP manualmente.
        3.  Se a chamada for bem-sucedida (status 200) e a lista de templates não estiver vazia, itera sobre cada template e chama `process-notification`.
        4.  Em caso de erro de conexão ou status HTTP inesperado, loga uma mensagem de erro e o ciclo termina, aguardando a próxima iteração.

*   **`process-notification [template]`**
    *   **Propósito:** Contém a lógica central para processar uma única notificação, incluindo o envio real do SMS.
    *   **Fluxo de Execução:**
        1.  Extrai `wabaId`, `id` e `category` do mapa do template.
        2.  Cria uma `notification-key` única (ex: `"template_abc_UTILITY"`).
        3.  Verifica se a `notification-key` **já existe** no `@sent-notifications-cache`. Se sim, loga uma mensagem e para.
        4.  Se a chave não existe, chama `get-contact-phone` para buscar o número do contato.
        5.  Se o contato for encontrado:
            *   Formata a mensagem de alerta.
            *   Chama `send-sms-via-api` para enviar a notificação.
            *   Se o envio for bem-sucedido (status 200), adiciona a `notification-key` ao `sent-notifications-cache` usando `swap!`.
            *   Se o envio falhar, loga um erro detalhado com a resposta da API.
        6.  Se o contato não for encontrado, loga um aviso.

## 4. Considerações para Evolução Futura

Esta implementação inicial como protótipo é funcional, mas foi projetada com vários pontos de evolução em mente para se alinhar com a **Arquitetura Alvo**.

1.  **Persistência de Dados (Idempotência):**
    *   **Situação Atual:** O cache de idempotência (`sent-notifications-cache`) é um `atom` em memória. Se o serviço reiniciar, ele perde todo o histórico e pode reenviar notificações.
    *   **Próximo Passo:** Substituir o `atom` por uma tabela em um banco de dados (ex: CockroachDB, PostgreSQL). A função `process-notification` faria um `SELECT` para verificar a existência da notificação e um `INSERT` após o envio.

2.  **Integração com Provedor de SMS Real:**
    *   **Situação Atual:** O envio de notificações é simulado com `println`.
    *   **Próximo Passo:** Integrar uma biblioteca cliente para um provedor de SMS (ex: Twilio, Vonage). A chamada a `println` seria substituída por uma chamada de API a este provedor.

3.  **Fonte de Dados de Contato:**
    *   **Situação Atual:** Os dados de contato são mockados em uma variável de ambiente.
    *   **Próximo Passo:** Substituir a chamada a `get-contact-phone` por uma chamada de API ao `customer-manager-service`, que se tornará a fonte da verdade para os dados dos clientes.

4.  **Logging Estruturado e Monitoramento:**
    *   **Situação Atual:** O logging é feito com `println`.
    *   **Próximo Passo:** Implementar uma biblioteca de logging estruturado (ex: `tools.logging` com SLF4J/Logback) para permitir a coleta e análise de logs em produção. Adicionar métricas de monitoramento (ex: número de notificações enviadas, erros de API).

5.  **Mecanismo de Retentativas (Retry):**
    *   **Situação Atual:** Se a chamada ao `notification-watcher` falhar, ela só será tentada novamente no próximo ciclo (1 minuto depois).
    *   **Próximo Passo:** Implementar uma lógica de retentativa com backoff exponencial (ex: usando a biblioteca `diehard`) para tornar a comunicação entre serviços mais resiliente a falhas transitórias.
