# Deploy no Render com Docker

Este documento fornece as instruções para fazer o deploy do serviço de notificação na plataforma [Render](https://render.com/), utilizando o `Dockerfile` presente no projeto.

## Tipo de Serviço

Para ser compatível com o plano gratuito do Render, o serviço deve ser implantado como um **Web Service**.

O serviço foi projetado para iniciar um servidor web mínimo que responde a requisições HTTP, satisfazendo os requisitos da plataforma, enquanto o processo principal de polling e notificação é executado em uma thread de background.

## Instruções de Configuração

1.  **Conecte seu repositório** Git ao Render.
2.  Crie um novo **Web Service**.
3.  Durante a criação, o Render irá detectar o `Dockerfile` no seu repositório. Selecione esta opção para o deploy.

4.  **Porta (Port)**:
    *   O `Dockerfile` expõe a porta `8080`. O Render detectará isso automaticamente.

5.  **Adicione as Variáveis de Ambiente** na aba "Environment" do seu serviço no Render.

    ### Variáveis Gerais
    *   `PORT`: `8080` (O Render usa esta variável para rotear o tráfego para o contêiner).
    *   `WATCHER_URL`: Aponte para a URL pública do seu serviço `notification-watcher` (ex: `https://notification-watcher.onrender.com`).
    *   `DATABASE_URL`: String de conexão para seu banco de dados PostgreSQL (ex: a partir de um DB gratuito no próprio Render).

    ### Dados de Contato
    *   `MOCK_CUSTOMER_DATA`: Cole a sua string JSON de contatos. **Atenção para a nova estrutura de objeto.**
        *   **Exemplo:** `'{"waba_id_1": {"name": "Cliente A", "phone": "+5511999998888", "email": "cliente.a@example.com"}}'`

    ### Credenciais de Canais
    *   **Canal de SMS:**
        *   `SMS_API_URL`: (Opcional se não for usar SMS) URL da API de SMS.
        *   `SMS_API_TOKEN`: (Opcional) Token da API de SMS.
        *   `SMS_API_USER`: (Opcional) Usuário da API de SMS.
    *   **Canal de Email:**
        *   `EMAIL_API_URL`: (Opcional) URL da API de Email.
        *   `EMAIL_API_TOKEN`: (Opcional se não for usar Email) Token da API de Email.
        *   `EMAIL_API_USER`: (Opcional) Usuário da API de Email.


Após salvar, o Render irá construir a imagem Docker, implantar e iniciar o serviço. Você poderá acompanhar a atividade nos "Logs" do serviço.
