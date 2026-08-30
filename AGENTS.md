# 企业员工数字化平台协作规范

本文件适用于仓库根目录及其子目录。项目是一个 Vue 3 前端与 Spring Boot 3 后端的单仓库；后端负责认证、组织与权限、知识库、文档解析、向量检索和 RAG 流式问答。

## 项目结构

```text
backend/                         Spring Boot 3.4 / Java 17 服务
  src/main/java/com/example/rag/
    config/                       MinIO、模型与 Spring Security 配置
    controller/                   HTTP API 和 SSE 入口
    security/                     JWT 解析与认证过滤器
    service/                      业务、持久化、解析、向量和模型服务
  src/main/resources/application.yml
  Dockerfile
frontend/                        Vue 3 + TypeScript + Element Plus
docs/init.sql                    PostgreSQL 首次初始化脚本
docker-compose.yml               PostgreSQL、Qdrant、MinIO、前后端编排
```

后端服务职责：

- `PlatformRepository`：使用 `JdbcTemplate` 访问 PostgreSQL，承载用户、角色、菜单、知识库、文档、分块和审计数据。
- `KnowledgeBaseController`：文档上传、MinIO 存储、异步解析、分块、嵌入和索引状态更新。
- `DocParseService`：Apache Tika 解析与 PDF OCR 配置。
- `ChunkService`：按字符大小和重叠参数切分可检索文本。
- `QdrantService`：向量集合创建、写入和按知识库范围检索。
- `DashScopeService`：DashScope OpenAI 兼容接口的嵌入、重排和对话调用。
- `RagController`：权限过滤后的检索、引用构造和 SSE 事件输出。

## 开发环境与命令

要求：JDK 17、Maven 3.9+、Docker Desktop。前端使用 Node.js 20+。

```powershell
# 后端编译与测试
cd backend
mvn test
mvn -DskipTests package

# 前端类型检查与构建
cd frontend
npx vue-tsc --noEmit
npm run build

# 全栈构建与启动（仓库根目录）
docker compose up -d --build
docker compose ps
docker compose logs --tail=100 backend

# 仅重建受影响的服务
docker compose build backend
docker compose up -d --no-deps backend
docker compose build frontend
docker compose up -d --no-deps frontend
```

服务端口：前端 `80`、后端 `8080`、PostgreSQL `5432`、MinIO `9000/9001`、Qdrant `6333/6334`。健康检查地址为 `http://localhost:8080/actuator/health`。

当本机没有 Maven 或依赖环境不完整时，使用 `docker compose build backend` 验证后端编译。提交前至少执行与变更范围对应的构建、类型检查或测试，并执行：

```powershell
git diff --check
```

## Java 与 Spring 编码约定

- 使用 Java 17 语法，四空格缩进，类名/方法名遵循标准 Java 命名；字符串、异常信息和用户可见文本使用清晰中文。
- 使用构造器注入；不要新增字段注入或静态服务定位器。
- 控制器只处理 HTTP 协议、请求校验和异常到状态码的映射；业务、外部服务调用和数据库操作放在 `service` 层。
- 新接口保持 `/api` 下现有资源风格，返回字段采用当前前端约定的 camelCase。变更既有字段、SSE 事件名或状态枚举前，必须同步前端并保留兼容方案。
- 对用户输入使用 `spring-boot-starter-validation` 或显式校验。无效请求返回 `400`，无认证返回 `401`，无权限返回 `403`，资源不存在返回 `404`。
- 处理请求的身份从 `@AuthenticationPrincipal` 获取；不要信任请求体中的用户、部门、角色或知识库范围。
- 数据访问使用参数化 SQL，禁止拼接外部输入形成 SQL、Qdrant filter、对象键或模型提示词。
- 不吞掉业务异常。仅启动时的可选初始化等明确的 best-effort 场景可以捕获异常，且应说明原因；文档解析和索引失败必须记录安全的错误信息并更新文档状态。
- 不记录 JWT、密码、DashScope API Key、MinIO 密钥、完整 Authorization 请求头或完整敏感文档内容。

## 变更边界与兼容性

- 保持 `docs/init.sql` 稳定。它只会在 PostgreSQL 数据卷首次创建时执行，修改它不会迁移已有环境。
- 涉及既有数据的结构或种子变更，应提供可重复执行的迁移或受控初始化逻辑；不得要求用户删除 `pgdata`、`qdrantdata` 或 `miniodata` 以使功能生效。
- 未经明确需求，不修改默认账号、密码、角色、权限标识、菜单 ID、API 路径或数据库主键。
- 权限校验必须位于后端。前端菜单隐藏、路由守卫和请求参数过滤只能改善体验，不能代替授权。
- 修改角色、菜单、部门或知识库可见性时，检查 `sys_user_role`、`sys_role_permission`、`kb_knowledge_base_dept` 以及 `PlatformRepository.KbScope` 的一致性。
- 修改配置项时同时更新 `application.yml`、Docker Compose 环境变量和 `.env.example`（若该变量面向部署用户），严禁提交真实密钥。
- 修改后端 API 合同时，联动更新 `frontend/src/api.ts`、相关 TypeScript 类型和调用页面；保留 `401` 清理 `rag_token` 并跳转 `/login` 的统一逻辑。

## 文档解析、存储与索引

上传及索引链路为：`MultipartFile` 校验 -> MinIO 上传 -> `kb_document` 建档 -> 异步解析 -> 分块 -> DashScope embedding -> PostgreSQL 分块记录 -> Qdrant upsert -> 状态更新。

- 支持的扩展名必须同时维护 `KnowledgeBaseController.ALLOWED` 与 `DocParseService.EXPECTED_MEDIA_TYPES`。扩展名与实际 MIME 类型不一致时必须拒绝上传。
- 上传前调用 `DocParseService.validateUpload`；解析和存储均使用受控的流与 `try-with-resources`，避免将最大 50MB 文件完整读入内存。
- PDF 保持 `PDFParserConfig.OCR_STRATEGY.AUTO`，中文 OCR 语言为 `chi_sim+eng`。调整 OCR、Tika 或解析器依赖时，同步检查 `backend/Dockerfile` 内的 `tesseract-ocr` 与 `tesseract-ocr-chi-sim` 安装。
- 解析结果在清洗后为空时必须标记为失败，保留可诊断但不泄露敏感正文的 `error_msg`；不得写入空分块或空向量。
- 修改分块策略时保持 `chunk_size`、`chunk_overlap`、`chunk_count` 和实际写入数量一致，并考虑重叠、Unicode 字符、极短文本和超长文本。
- Qdrant payload 至少保留 `chunk_id`、`doc_id`、`kb_id`、`content`、`file_name`、`visibility` 和 `dept_id`。检索结果的引用字段须同时兼容旧 payload 的 `snake_case` 与前端 `camelCase`。
- 嵌入向量维度必须与 `dashscope.embedding-dimensions` 和 Qdrant collection 的 `vectors.size` 一致。变更维度、嵌入模型或 payload 结构前，规划全量重建索引。
- 删除或重建文档时，确保 PostgreSQL 记录、Qdrant points 和 MinIO 对象的生命周期策略一致，避免已删除文档仍被检索。

## RAG、SSE 与外部模型

- RAG 查询先根据当前用户的 `KbScope` 计算可访问知识库，再传给 Qdrant filter；不得因为前端传入 `kbIds` 而扩大范围。
- 送入模型的上下文只能来自已授权的命中分块。引用返回时包含稳定的 `chunkId`、`docId`、`kbId`、`fileName`、`snippet`，有真实页码时才返回 `pageNo`。
- SSE 事件保持 JSON `data`：`citations`（或兼容的历史 `citation`）、`chunk`、`done`、`error`。`chunk` 使用 `delta` 字段；新增事件或字段需让前端解析器容错。
- 流式请求、Qdrant、MinIO 和 DashScope 调用必须有明确的超时、错误路径和用户可理解的错误反馈。不要把供应商原始异常、密钥或完整提示词直接返回给客户端。
- 新增模型或第三方 SDK 前，优先复用现有 `DashScopeService`、`RagModelConfig` 和 `RestClient` 模式，避免在控制器中直接发起外部请求。

## 代码审查清单

- 变更是否只覆盖需求所需文件，且没有回退或格式化无关改动？
- 是否验证了认证、角色、部门和知识库数据权限，且无法通过直接 API 调用绕过？
- 数据库、MinIO、Qdrant 与 PostgreSQL 状态在成功、失败、重试和删除时是否一致？
- 文件类型、大小、完整性、空文本和 OCR 失败是否有明确处理？
- API、SSE、前端类型和用户可见错误信息是否一致？
- 是否运行了适当的 Maven/前端构建或测试，并在交付说明中说明未运行的检查及原因？
- 是否确认 `git diff --check` 通过，且未提交密钥、构建产物或本地卷数据？
