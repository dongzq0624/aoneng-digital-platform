# 后端多模块架构

## 1. 模块职责与依赖

| 模块 | 职责 | 允许依赖 |
| --- | --- | --- |
| `rag-common` | 通用异常、统一错误响应、常量、无业务工具和跨模块 DTO | JDK、无业务的基础库 |
| `rag-framework` | Spring 配置、配置属性、线程池、JWT 工具与过滤器、安全链、全局异常处理 | `rag-common` 和基础设施 SDK |
| `rag-biz` | HTTP Controller、业务 Service、数据库访问、文档解析、对象存储、向量检索和 RAG 编排 | `rag-common`、`rag-framework` |
| `rag-task` | 定时任务、消息消费和重试入口 | `rag-biz`，只能调用公开 Service 接口 |

依赖方向固定为：

```text
rag-common <- rag-framework <- rag-biz <- rag-task
```

禁止反向依赖，禁止 `rag-common` 引用业务类，禁止 `rag-framework` 引用 Controller 或具体业务实现，禁止 `rag-biz` 引用 `rag-task`。这样可以避免循环依赖，并支持未来将任务模块拆成独立部署单元。

## 2. 当前目录

```text
backend
├── pom.xml                         # rag-parent 聚合父工程和统一版本
├── rag-common
│   └── src/main/java/com/example/rag/common
│       └── exception                # ApiErrorResponse 和业务异常
├── rag-framework
│   └── src/main/java/com/example/rag/framework
│       ├── advice                   # @RestControllerAdvice
│       ├── config                   # Spring、MinIO、Qdrant、模型和线程池配置
│       └── security                 # JWT 解析、认证过滤器和安全适配
├── rag-biz
│   └── src/main/java/com/example/rag
│       ├── RagApplication.java
│       ├── auth/controller          # 登录、当前用户接口
│       ├── auth/dto                 # LoginRequest、LoginResponse、UserSummary
│       ├── doc/controller           # 知识库和文档 HTTP 接口
│       ├── chat/controller          # RAG 对话和 SSE 接口
│       ├── system/controller       # 用户、部门、角色和菜单接口
│       ├── audit/controller         # 审计查询接口
│       ├── dashboard/controller    # 仪表盘接口
│       └── service                  # 现有业务实现，按领域逐步拆分
└── rag-task
    └── src/main/java                # 预留任务和消息消费入口
```

当前 `service` 下仍有兼容性的 `PlatformRepository` 和若干服务类，保证现有 API、数据库和前端调用不变。它们属于 `rag-biz`，不会被新模块引用；新增代码不得继续扩大这个顶层混合包。

## 3. 业务模块目标分层

```text
com.example.rag.doc
├── controller       # 仅 HTTP、@Valid、调用 Service、返回 VO/Result
├── dto              # 前端入参
├── vo               # 脱敏后的前端出参
├── po               # 数据库实体，仅 Mapper/Service 内部使用
├── mapper           # 参数化 CRUD；复杂 SQL 放 resources/mybatis/mapper/*.xml
├── service
│   ├── IDocumentService.java
│   └── impl/DocumentServiceImpl.java
└── convert          # MapStruct DTO <-> PO <-> VO
```

`chat`、`vector`、`system`、`audit` 和 `dashboard` 按同样的领域边界组织。Controller 不注入 Mapper、JdbcTemplate、MinioClient、Qdrant 客户端或模型客户端；事务只放在 ServiceImpl 的 public 方法上。定时任务只依赖 Service 接口，不复制业务逻辑。

## 4. 配置与环境

应用配置位于 `rag-biz/src/main/resources/application.yml`，可通过 `application-dev.yml`、`application-prod.yml` 覆盖。MinIO、Qdrant、PostgreSQL、JWT 和 DashScope 的密钥只从环境变量读取，现有变量名和默认行为保持兼容。框架配置属性集中在 `rag-framework`，由启动类的 `@ConfigurationPropertiesScan` 注册。

## 5. 后续迁移顺序

1. 将 `PlatformRepository` 按 `system`、`doc`、`chat`、`audit`、`dashboard` 拆为领域 Mapper 和 Service。
2. 为文档、对话和向量领域补齐 PO、DTO、VO 以及 MapStruct Convert，并迁移 Controller。
3. 将复杂 SQL 移入 MyBatis Mapper XML，保留参数化查询和现有字段兼容性。
4. 在 `rag-task` 增加可配置的定时任务/消费开关、幂等和重试策略，只调用业务 Service。
5. 为认证、权限范围、文档索引失败、SSE 错误和全局异常增加单元测试与 MockMvc 集成测试。

## 6. 构建验证

```bash
cd backend
mvn clean test
mvn -DskipTests package
git diff --check
```
