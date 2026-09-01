FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY rag-common/pom.xml rag-common/pom.xml
COPY rag-domain/pom.xml rag-domain/pom.xml
COPY rag-infrastructure/pom.xml rag-infrastructure/pom.xml
COPY rag-biz/pom.xml rag-biz/pom.xml
RUN mvn -q -DskipTests dependency:go-offline
COPY rag-domain/src rag-domain/src
COPY rag-infrastructure/src rag-infrastructure/src
COPY rag-biz/src rag-biz/src
COPY rag-common/src rag-common/src
RUN mvn -q -DskipTests package
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl tesseract-ocr tesseract-ocr-chi-sim \
    && rm -rf /var/lib/apt/lists/*
COPY --from=build /app/rag-biz/target/rag-biz-1.0.0.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","app.jar"]
