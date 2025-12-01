# Etapa 1: Build - Compilação do projeto com Leiningen
FROM clojure:lein-2.9.1 as builder

# Defina o diretório de trabalho
WORKDIR /app

# Copie o arquivo de definição do projeto primeiro (otimização de cache do Docker)
COPY project.clj .

# Baixe as dependências (cacheado se project.clj não mudar)
RUN lein deps

# Copie o código-fonte da aplicação
COPY src ./src

# Compile a aplicação em um uberjar
RUN lein uberjar

# -----------------------------------------------------------
# Etapa 2: Execução - Imagem leve (JRE) para rodar o sistema
# CORREÇÃO: 'openjdk:11-jre-slim' foi descontinuada. 
# Usamos 'eclipse-temurin' que é a sucessora oficial.
# -----------------------------------------------------------
FROM eclipse-temurin:11-jre-jammy

# Defina o diretório de trabalho
WORKDIR /app

# Copie o JAR gerado na etapa anterior
# Certifique-se de que o nome do arquivo aqui bate com o gerado no project.clj
COPY --from=builder /app/target/uberjar/sms-notifier-0.2.0-SNAPSHOT-standalone.jar ./app.jar

# Defina as variáveis de ambiente (valores padrão)
ENV WATCHER_URL="http://localhost:8080" \
    MOCK_CUSTOMER_DATA='{}' \
    PORT="8080"

# Exponha a porta
EXPOSE 8080

# Comando para iniciar a aplicação
CMD ["java", "-jar", "app.jar"]
