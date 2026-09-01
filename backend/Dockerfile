FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY rag-common/pom.xml rag-common/pom.xml
COPY rag-framework/pom.xml rag-framework/pom.xml
COPY rag-biz/pom.xml rag-biz/pom.xml
COPY rag-task/pom.xml rag-task/pom.xml
RUN mvn -q -DskipTests dependency:go-offline
COPY rag-biz/src rag-biz/src
COPY rag-common/src rag-common/src
COPY rag-framework/src rag-framework/src
RUN mvn -q -DskipTests package
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN apt-get update \
    && apt-get install -y --no-install-recommends tesseract-ocr tesseract-ocr-chi-sim \
    && rm -rf /var/lib/apt/lists/*
COPY --from=build /app/rag-biz/target/rag-biz-1.0.0.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","app.jar"]
