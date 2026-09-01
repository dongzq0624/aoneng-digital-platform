# 企业员工数字化平台后端

Spring Boot 4.1.1 / Java 21 多模块后端工程，覆盖认证、组织权限、知识库、文档解析、向量检索和 RAG 流式问答。

## 模块划分

| 模块 | 职责 |
|------|------|
| `rag-common` | 跨模块工具：常量、异常、响应包装 |
| `rag-domain` | 领域层：PO、Mapper 接口、Mapper XML |
| `rag-framework` | 基础设施：JWT 认证、配置类、外部客户端（DashScope/Qdrant/MinIO） |
| `rag-biz` | 业务层：Controller、Service、DTO、MapStruct 转换器 |

## 技术栈

- **运行时**：Spring Boot 4.1.1、Spring Security 6、Spring WebFlux（流式问答）、Flyway
- **持久化**：MyBatis-Plus 3.5.17、PostgreSQL、Qdrant、MinIO
- **安全**：JWT（jjwt 0.12）、BCrypt 密码编码、Spring Security 方法级安全
- **AI/向量**：DashScope（兼容 OpenAI 接口）、Qdrant HTTP API
- **文档解析**：Apache Tika + PDFBox（含 Tesseract OCR）
- **响应包装**：`Result<T>` 统一响应 + `GlobalExceptionHandler`
- **配置**：`@ConfigurationProperties` 类型安全绑定

## 开发命令

```powershell
# 进入后端目录
cd backend

# 编译
mvn compile

# 运行测试
mvn test

# 跳过测试打包
mvn -DskipTests package

# 多环境启动
mvn spring-boot:run -pl rag-biz -Dspring-boot.run.profiles=dev
```

## Docker 化部署

```powershell
# 在仓库根目录执行
docker compose up -d --build
docker compose logs --tail=100 backend
```

## API 版本

所有 REST API 路径以 `/api/v1/` 为前缀。完整 OpenAPI 文档：`http://localhost:8080/swagger-ui.html`

## 环境变量

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `PG_HOST` | localhost | PostgreSQL 主机 |
| `PG_DB` | rag_platform | 数据库名 |
| `PG_USER` | rag | 数据库用户 |
| `PG_PASSWORD` | rag123456 | 数据库密码 |
| `DASHSCOPE_API_KEY` | （空） | DashScope API 密钥（必填） |
| `QDRANT_HOST` | localhost | Qdrant 主机 |
| `QDRANT_PORT` | 6333 | Qdrant 端口 |
| `MINIO_ENDPOINT` | http://localhost:9000 | MinIO 端点 |
| `MINIO_ACCESS_KEY` | minioadmin | MinIO 用户名 |
| `MINIO_SECRET_KEY` | minioadmin | MinIO 密码 |
| `JWT_SECRET` | （开发环境默认值） | JWT 签名密钥（≥32 字符） |

## 多环境

- `application.yml`：公共配置
- `application-dev.yml`：开发环境（启用 DEBUG 日志、暴露详细健康信息）
- `application-prod.yml`：生产环境（WARN 日志、隐藏健康详情）

切换：`SPRING_PROFILES_ACTIVE=prod`
