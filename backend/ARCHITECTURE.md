# 后端架构

## 技术基线

- Spring Boot 4.1.1
- Java 21（Maven release 21）
- Maven 3.9+
- PostgreSQL 16、Flyway、MyBatis-Plus
- Milvus、MinIO、DashScope

Docker、CI 和本地开发统一使用 Java 21。

## 模块划分

```text
rag-common
  通用结果、异常和常量，不依赖业务模块

rag-domain
  领域 PO、领域仓储接口、MyBatis Mapper 接口和 XML

rag-infrastructure
  Spring 配置、安全过滤器、数据库/对象存储/向量库/模型适配器
  依赖 rag-common 和 rag-domain

rag-biz
  启动类、HTTP Controller、DTO/VO、应用服务和业务编排
  依赖 rag-common、rag-domain 和 rag-infrastructure
```

依赖方向：

```text
rag-common <- rag-domain <- rag-infrastructure <- rag-biz
```

禁止反向依赖。Controller 不得直接调用 MyBatis、MinIO、Milvus 或模型 SDK；业务编排应依赖基础设施接口。

## 启动与配置

启动类位于 `rag-biz/src/main/java/com/aoneng/rag/RagApplication.java`，使用 `@ConfigurationPropertiesScan` 和 `@MapperScan` 注册配置与 Mapper。

应用配置位于 `rag-biz/src/main/resources`：

- `application.yml`：通用配置和环境变量占位符
- `application-dev.yml`：开发日志和本地诊断配置
- `application-prod.yml`：生产日志、健康检查和监控配置

生产环境必须通过环境变量或密钥管理系统提供数据库、JWT、MinIO 和 DashScope 凭据，不得依赖开发默认值。

## 运行与验证

```powershell
cd backend
mvn test
mvn -DskipTests package
docker build -t aoneng-rag-backend .
```

全栈启动在仓库根目录执行：

```powershell
docker compose up -d --build
```
