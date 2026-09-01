# 企业员工数字化平台后端

基于 Spring Boot 4.1.1、Java 21 的 Maven 多模块后端，覆盖认证、组织权限、知识库、文档解析、向量检索和 RAG 流式问答。

## 模块

| 模块 | 职责 |
| --- | --- |
| `rag-common` | 通用常量、异常、统一响应和分页模型 |
| `rag-domain` | 领域 PO、仓储接口、MyBatis Mapper 和 XML |
| `rag-infrastructure` | Spring 配置、安全、PostgreSQL/MinIO/Milvus/DashScope 适配器 |
| `rag-biz` | 启动类、Controller、DTO、VO 和应用服务 |

## 技术基线

- Java 21，Maven 编译 release 21
- Spring Boot 4.1.1
- Spring MVC、WebFlux、Validation、Security、Actuator
- MyBatis-Plus、PostgreSQL、Flyway
- Apache Tika、Tesseract OCR、MinIO、Milvus、DashScope

## 开发命令

```powershell
cd backend
mvn test
mvn -DskipTests package
mvn spring-boot:run -pl rag-biz -Dspring-boot.run.profiles=dev
```

## Docker

从仓库根目录执行：

```powershell
docker compose up -d --build
docker compose logs --tail=100 backend
```

后端端口为 `8080`，健康检查地址为 `http://localhost:8080/actuator/health`。

## 环境变量

生产环境必须显式配置以下变量，不应使用默认凭据：

`PG_HOST`、`PG_DB`、`PG_USER`、`PG_PASSWORD`、`JWT_SECRET`、`DASHSCOPE_API_KEY`、`MILVUS_HOST`、`MILVUS_PORT`、`MILVUS_TOKEN`、`MILVUS_DIMENSION`、`MILVUS_SCORE_THRESHOLD`、`MINIO_ENDPOINT`、`MINIO_ACCESS_KEY`、`MINIO_SECRET_KEY`。

配置文件：

- `application.yml`：通用配置
- `application-dev.yml`：开发 profile
- `application-prod.yml`：生产 profile
