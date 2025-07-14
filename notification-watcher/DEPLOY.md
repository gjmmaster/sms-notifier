# Deploy no Render

Este documento fornece as instruções para fazer o deploy do serviço `notification-watcher` na plataforma [Render](https://render.com/).

## Tipo de Serviço

O `notification-watcher` deve ser implantado como um **Web Service** no Render, pois ele precisa expor uma porta HTTP (`8080` por padrão) para que o `sms-notifier-prototype` possa se conectar a ele.

## Instruções de Configuração

1.  **Conecte seu repositório** Git ao Render.
2.  Crie um novo **Web Service**.
3.  Use as seguintes configurações durante a criação do serviço:

    *   **Build Command**:
        ```sh
        ./build.sh
        ```

    *   **Start Command**:
        ```sh
        java -jar target/uberjar/notification-watcher-0.1.0-SNAPSHOT-standalone.jar
        ```
        *Nota: O nome do arquivo JAR pode variar. Verifique o nome exato no diretório `target/uberjar` após a primeira build.*

4.  **Porta (Port)**:
    *   O Render detectará automaticamente que o serviço está escutando na porta `8080`. Certifique-se de que a configuração de porta no Render corresponda à porta que o serviço usa (definida pela variável de ambiente `PORT` ou o padrão `8080`).

5.  **Adicione as Variáveis de Ambiente** na aba "Environment" do seu serviço no Render:
    *   **Para o protótipo**, use o modo mock para não depender de chaves de API reais:
        *   `GUPSHUP_MOCK_MODE`:
            *   **Valor:** `true`
        *   `MOCK_CUSTOMER_MANAGER_WABA_IDS`:
            *   **Valor:** `waba_id_1,waba_id_2` (ou os mesmos IDs que você configurou no `sms-notifier-prototype`).
    *   **Para produção** (no futuro):
        *   `GUPSHUP_TOKEN`: Seu token de API real da Gupshup.
        *   `CUSTOMER_MANAGER_URL`: A URL do `customer-manager-service` quando ele for desenvolvido.

Após salvar, o Render irá construir, implantar e iniciar o serviço. Ele receberá uma URL pública (ex: `notification-watcher.onrender.com`) que você deverá usar na configuração da variável de ambiente `WATCHER_URL` do serviço `sms-notifier-prototype`.
