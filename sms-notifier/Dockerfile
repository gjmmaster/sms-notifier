# Etapa 1: Build - Use a imagem oficial do Leiningen para compilar o projeto
FROM clojure:lein-2.9.1 as builder

# Defina o diretório de trabalho dentro do contêiner
WORKDIR /app

# Copie os arquivos de definição do projeto primeiro para aproveitar o cache do Docker
COPY project.clj .
# Baixe as dependências. Isso será cacheado se o project.clj não mudar.
RUN lein deps

# Copie o código-fonte da aplicação
COPY src ./src

# Compile a aplicação em um uberjar (JAR auto-suficiente)
RUN lein uberjar

# Etapa 2: Execução - Use uma imagem leve com apenas o Java Runtime Environment (JRE)
FROM openjdk:11-jre-slim

# Defina o diretório de trabalho
WORKDIR /app

# --- LINHA CORRIGIDA ---
# Copie o uberjar com a VERSÃO CORRETA da etapa de build para a imagem final
COPY --from=builder /app/target/uberjar/sms-notifier-0.2.0-SNAPSHOT-standalone.jar ./app.jar

# Defina as variáveis de ambiente (serão substituídas pelas configurações do Render, se fornecidas)
ENV WATCHER_URL="http://localhost:8080"
ENV MOCK_CUSTOMER_DATA='{}'
ENV PORT="8080"

# Exponha a porta que o servidor web irá usar
EXPOSE 8080

# Comando para executar a aplicação quando o contêiner iniciar
CMD ["java", "-jar", "app.jar"]
