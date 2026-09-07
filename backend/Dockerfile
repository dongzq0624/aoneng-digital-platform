FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY backend/pom.xml backend/pom.xml
COPY backend/rag-common/pom.xml backend/rag-common/pom.xml
COPY backend/rag-domain/pom.xml backend/rag-domain/pom.xml
COPY backend/rag-infrastructure/pom.xml backend/rag-infrastructure/pom.xml
COPY backend/rag-biz/pom.xml backend/rag-biz/pom.xml
RUN mvn -q -f backend/pom.xml -DskipTests dependency:go-offline
COPY backend/rag-domain/src backend/rag-domain/src
COPY backend/rag-infrastructure/src backend/rag-infrastructure/src
COPY backend/rag-biz/src backend/rag-biz/src
COPY backend/rag-common/src backend/rag-common/src
RUN mvn -q -f backend/pom.xml -DskipTests package
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl tesseract-ocr tesseract-ocr-chi-sim \
    && rm -rf /var/lib/apt/lists/*
RUN mkdir -p /models/qwen3-embedding-tokenizer
COPY --from=build /app/backend/rag-biz/target/rag-biz-1.0.0.jar app.jar
COPY backend/models/qwen3-embedding-tokenizer /models/qwen3-embedding-tokenizer
EXPOSE 8080
ENTRYPOINT ["java","-jar","app.jar"]
