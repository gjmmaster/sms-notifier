# Documentação Técnica Detalhada: Serviço `sms-notifier`

**ID do Documento:** DOC-SN-CORE-20250723-v1.1
**Data:** 23 de Julho de 2025
**Versão:** 1.1 (Reflete a implementação com persistência de dados e resiliência)
**Autores:** Jules (AI Assistant), Contribuidores do Projeto

## 1. Propósito

Este documento fornece uma descrição técnica detalhada da implementação atual do microsserviço `sms-notifier`, com foco no seu arquivo principal `core.clj`. O objetivo é servir como uma referência técnica e guia de onboarding para desenvolvedores (humanos ou IA), detalhando a arquitetura dos componentes, fluxos de dados, configuração e a lógica de negócio implementada.

## 2. Visão Geral do Serviço

O `sms-notifier` é um serviço em Clojure desenhado para atuar como o componente final no fluxo de notificação do sistema SNCT. Suas responsabilidades são:

1.  **Consumir Dados de Mudanças:** Periodicamente (a cada 4 minutos), consultar o endpoint `/changed-templates` do serviço `notification-watcher` para obter a lista de templates que tiveram suas categorias alteradas.
2.  **Mapear Contatos:** Para cada template alterado, identificar o cliente correspondente (via `wabaId`) e buscar seu número de telefone de contato em uma fonte de dados configurável.
3.  **Garantir Idempotência com Persistência:** Assegurar que uma notificação seja enviada apenas uma vez, utilizando um **banco de dados PostgreSQL** para persistir as chaves de cada notificação enviada. Isso garante que, mesmo após uma reinicialização, o serviço não envie alertas duplicados.
4.  **Enviar Notificações via API Externa:** Formatar uma mensagem de alerta e enviá-la através de uma chamada de API a um provedor de SMS externo.
5.  **Operar como Web Service:** Expor um endpoint HTTP (`/`) para ser compatível com plataformas de deploy como o Render, enquanto o trabalho principal é executado em background.
6.  **Garantir Resiliência:** Utilizar um padrão **Circuit Breaker** para gerenciar a conexão com o banco de dados, prevenindo falhas em cascata caso o banco se torne indisponível.

## 3. Arquitetura Detalhada e Componentes do `core.clj`

### 3.1. Configuração e Variáveis de Ambiente

O serviço é configurado exclusivamente por variáveis de ambiente, acessadas via `environ`.

* **`WATCHER_URL`** (String): URL base do `notification-watcher`.
* **`MOCK_CUSTOMER_DATA`** (String): String JSON usada para mapear `wabaId` a números de telefone. Essencial para testes.
* **`PORT`** (String): Porta TCP para o servidor web integrado (http-kit). Padrão: `"8080"`.
* **`DATABASE_URL`** (String): **(Novo)** String de conexão JDBC para o banco de dados PostgreSQL. Ex: `"jdbc:postgresql://user:pass@host:port/dbname"`.
* **`SMS_API_URL`**, **`SMS_API_TOKEN`**, **`SMS_API_USER`** (Strings): **(Novos)** Credenciais necessárias para autenticar e enviar mensagens através da API de SMS externa.

### 3.2. Estado Global e Persistência

* **`sent-notifications-cache`** (`clojure.lang.Atom`): Atua como um **cache rápido em memória** (um `set`) das chaves de notificação que já foram enviadas.
    * **Gerenciamento:** Este cache é populado na inicialização do serviço pela função `load-keys-from-db!`, que lê todas as chaves existentes do banco de dados. Após o envio bem-sucedido de uma nova notificação, a chave é adicionada tanto ao banco quanto a este cache.
* **`mock-customer-data`** (`clojure.lang.Atom`): Armazena os dados de contato mockados da variável de ambiente.
* **`circuit-breaker-state`** (`clojure.lang.Atom`): **(Novo)** Mantém o estado do Circuit Breaker, controlando se as operações com o banco de dados estão permitidas (`:closed`) ou temporariamente bloqueadas (`:open`).

### 3.3. Ponto de Entrada e Lógica Principal

* **`-main [& args]`**: Ponto de entrada da aplicação.
    * **Funcionalidade Atualizada:**
        1.  Chama `load-keys-from-db!` para popular o cache de idempotência.
        2.  Chama `parse-customer-data` para carregar contatos mockados.
        3.  **Executa uma verificação de segurança:** se `DATABASE_URL` está definida mas o cache continua vazio, o serviço entra em modo de segurança e não inicia o worker para evitar reenvios em massa.
        4.  Se a verificação passar, chama `start-notifier-loop!` para iniciar o polling em background.
        5.  Inicia o servidor web `http-kit`.

* **`start-notifier-loop!`**: Inicia um loop infinito em uma `future`. O ciclo de polling foi ajustado para ocorrer a cada **4 minutos** (`Thread/sleep 240000`).

### 3.4. Lógica de Negócio: Fluxo de Processamento de Notificação

* **`with-db-circuit-breaker`** (Macro): **(Novo)** Envolve todas as operações de banco de dados. Verifica o estado do `circuit-breaker-state` antes de executar o código. Se o circuito estiver "aberto", a operação é abortada imediatamente. Em caso de falha, aciona `trip-circuit-breaker!`.

* **`process-notification [template]`**: Contém a lógica central para uma única notificação.
    * **Fluxo de Execução Atualizado:**
        1.  Cria a `notification-key` a partir do template.
        2.  Verifica se a chave já existe no cache em memória (`@sent-notifications-cache`). Se sim, ignora.
        3.  Se a chave é nova, busca o telefone de contato.
        4.  Se o contato for encontrado, chama `send-sms-via-api` para enviar a notificação.
        5.  Se o envio do SMS for bem-sucedido (status 200), a lógica de persistência é acionada dentro do `with-db-circuit-breaker`, chamando `save-notification-key!` para salvar a chave no banco de dados e no cache.
        6.  Se o envio falhar, ou se o contato não for encontrado, uma mensagem de erro/aviso é registrada.

## 4. Considerações para Evolução Futura

Muitos dos próximos passos da versão anterior foram concluídos. O foco da evolução futura é:

1.  **Atualização da Suíte de Testes:** **(Prioridade Alta)** Os testes unitários em `core_test.clj` estão desatualizados. É crucial atualizá-los para mockar as interações com o banco de dados e testar a lógica do Circuit Breaker e o novo fluxo de `process-notification`.
2.  **Logging Estruturado:** Substituir as chamadas `println` por uma biblioteca de logging mais robusta (como `tools.logging` com SLF4J/Logback) para facilitar a observabilidade em produção.
3.  **Fonte de Dados de Contato Real**: Substituir o uso da variável `MOCK_CUSTOMER_DATA` por uma chamada de API a um serviço de gerenciamento de clientes, que se tornará a fonte da verdade para os dados dos clientes.
4.  **Escalabilidade Horizontal**: A implementação atual é para uma única instância. Para rodar múltiplas instâncias do `sms-notifier`, seria necessário implementar um mecanismo de locking distribuído (ex: via Redis ou a nível de banco de dados) para evitar que duas instâncias processem a mesma notificação ao mesmo tempo.
