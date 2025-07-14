# Deploy no Render com Docker

Este documento fornece as instruções para fazer o deploy do serviço `notification-watcher` na plataforma [Render](https://render.com/), utilizando o `Dockerfile` presente no projeto.

## Tipo de Serviço

O `notification-watcher` deve ser implantado como um **Web Service** no Render, pois ele precisa expor uma porta HTTP para que o `sms-notifier` possa se conectar a ele.

## Instruções de Configuração

1.  **Conecte seu repositório** Git ao Render.
2.  Crie um novo **Web Service**.
3.  Durante a criação, o Render irá detectar o `Dockerfile` no seu repositório. Selecione esta opção para o deploy. O Render se encarregará de construir a imagem Docker e executar o contêiner. Os comandos de build e de início já estão definidos no `Dockerfile`.

4.  **Porta (Port)**:
    *   O `Dockerfile` expõe a porta `8080`. O Render detectará isso automaticamente. Certifique-se de que a configuração de porta no Render esteja definida como `8080`.

5.  **Adicione as Variáveis de Ambiente** na aba "Environment" do seu serviço no Render. Estas irão sobrescrever os valores `ENV` definidos no `Dockerfile`.
    *   **Para o protótipo**, use o modo mock para não depender de chaves de API reais:
        *   `GUPSHUP_MOCK_MODE`:
            *   **Valor:** `true`
        *   `MOCK_CUSTOMER_MANAGER_WABA_IDS`:
            *   **Valor:** `waba_id_1,waba_id_2` (ou os mesmos IDs que você configurou no `sms-notifier`).
        *   `PORT`:
            *   **Valor:** `8080` (O Render usa esta variável para rotear o tráfego, mesmo que o `Dockerfile` já exponha a porta).
    *   **Para produção** (no futuro):
        *   `GUPSHUP_TOKEN`: Seu token de API real da Gupshup.
        *   `CUSTOMER_MANAGER_URL`: A URL do `customer-manager-service` quando ele for desenvolvido.

Após salvar, o Render irá construir a imagem Docker, implantar e iniciar o serviço. Ele receberá uma URL pública (ex: `notification-watcher.onrender.com`) que você deverá usar na configuração da variável de ambiente `WATCHER_URL` do serviço `sms-notifier`.
