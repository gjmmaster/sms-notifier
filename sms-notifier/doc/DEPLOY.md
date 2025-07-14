# Deploy no Render com Docker

Este documento fornece as instruções para fazer o deploy do serviço `sms-notifier` na plataforma [Render](https://render.com/), utilizando o `Dockerfile` presente no projeto.

## Tipo de Serviço

Para ser compatível com o plano gratuito do Render, o `sms-notifier` deve ser implantado como um **Web Service**.

O serviço foi projetado para iniciar um servidor web mínimo que responde a requisições HTTP, satisfazendo os requisitos da plataforma, enquanto o processo principal de polling e notificação é executado em uma thread de background.

## Instruções de Configuração

1.  **Conecte seu repositório** Git ao Render.
2.  Crie um novo **Web Service**.
3.  Durante a criação, o Render irá detectar o `Dockerfile` no seu repositório. Selecione esta opção para o deploy. Os comandos de build (`lein uberjar`) e de início (`java -jar app.jar`) já estão definidos dentro do `Dockerfile`.

4.  **Porta (Port)**:
    *   O `Dockerfile` expõe a porta `8080`. O Render detectará isso automaticamente. Certifique-se de que a configuração de porta no Render esteja definida como `8080`.

5.  **Adicione as Variáveis de Ambiente** na aba "Environment" do seu serviço no Render. Estas irão sobrescrever os valores `ENV` definidos no `Dockerfile`.
    *   `WATCHER_URL`: Aponte para a URL pública do seu serviço `notification-watcher` no Render (ex: `https://notification-watcher.onrender.com`).
    *   `MOCK_CUSTOMER_DATA`: Cole a sua string JSON de contatos mockados. Ex: `'{"waba_id_1": "+5511999998888"}'`.
    *   `PORT`:
        *   **Valor:** `8080` (O Render usa esta variável para rotear o tráfego corretamente para o contêiner).

Após salvar, o Render irá construir a imagem Docker a partir do `Dockerfile`, implantar e iniciar o serviço. A URL pública dele mostrará uma mensagem de status, e o worker de notificação estará ativo. Você poderá acompanhar a atividade (consultas e simulação de envio de SMS) na aba "Logs" do serviço.
