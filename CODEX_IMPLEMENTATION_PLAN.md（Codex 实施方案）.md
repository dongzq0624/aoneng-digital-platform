# 企业员工数字化平台（RAG 知识库）· Codex 实施方案

> 技术栈：Vue3 + TypeScript · Spring Boot + LangChain4j · Qdrant · Docker
> 模型：阿里云百炼 DashScope（qwen-plus / text-embedding-v4 / qwen3-rerank）
> 本文档是交给 Codex 编码的规格说明书：按 Phase 顺序执行，每个 Phase 有明确任务、关键代码要点与验收标准。

---

## 0. 项目总览

### 0.1 目标
构建企业员工数字化平台，核心能力为**知识库管理 + RAG 智能问答**，附带用户/权限、文档管理、审计日志。交付形态为前后端分离全栈应用，Docker Compose 一键部署，模型走阿里云百炼（数据与密钥安全可控）。

### 0.2 范围
| 模块 | 范围 |
|---|---|
| 认证与权限 | 登录/JWT、用户、部门、角色、RBAC 权限点 |
| 知识库管理 | 知识库 CRUD、可见范围（PRIVATE/DEPT/ORG/PUBLIC）、文档上传/版本/删除 |
| 文档处理 | Tika 解析 PDF/Word/Excel/PPT/MD → 分块 → 向量化 → 入 Qdrant |
| RAG 问答 | 权限过滤 → 向量召回 → qwen3-rerank 精排 → qwen-plus 生成 → 引用溯源（SSE 流式） |
| 审计 | 操作审计 + 问答留痕 + 反馈闭环 |

### 0.3 关键决策（先读这里）
- **后端**：Spring Boot 3.4.x + Java 17，ORM 用 MyBatis-Plus（贴合 RuoYi 系团队习惯，也可换 JPA）。
- **AI 编排**：LangChain4j 1.13.1，**手工编排 RAG 流水线**（不用 AiServices 黑盒），便于权限过滤、引用溯源与审计。
- **模型接入**：统一走 DashScope **OpenAI 兼容端点** `https://dashscope.aliyuncs.com/compatible-mode/v1`，ChatModel/EmbeddingModel 用 langchain4j-open-ai 即可，无需额外 SDK。
- **重排序**：用百炼 `qwen3-rerank`（hybrid 模式，综合语义+BM25），自定义实现 LangChain4j `Reranker` 接口。
- **混合检索**：主路径 = 向量召回(TopK=20) + qwen3-rerank(hybrid) 精排(Top5)；hybrid 已含关键词匹配，无需另起 BM25 服务。
- **权限隔离**：Qdrant point payload 挂 `kb_id / visibility / dept_id`，检索时用 filter 强制过滤（确定性规则，不靠 Prompt）。
- **流式**：Spring MVC `SseEmitter` + 前端 `fetch` 流式解析。

---

## 1. 技术栈与版本清单

| 层 | 技术 | 版本 |
|---|---|---|
| 前端框架 | Vue 3 + TypeScript + Vite | Vue 3.5.x / TS 5.x / Vite 6.x |
| UI 组件 | Element Plus | 2.x |
| 状态/路由 | Pinia / Vue Router | 2.x / 4.x |
| HTTP/SSE | Axios / fetch(ReadableStream) | 1.x / 内置 |
| 后端框架 | Spring Boot / JDK | 3.4.x / 17 (LTS) |
| AI 编排 | LangChain4j + langchain4j-qdrant + langchain4j-open-ai | 1.13.1 |
| ORM | MyBatis-Plus (spring-boot3 starter) | 3.5.7+ |
| 安全 | Spring Security + JWT (jjwt) | 6.x / 0.12.x |
| 文档解析 | Apache Tika (parsers-standard-package) | 2.9.x |
| 向量库 | Qdrant（Docker） | 1.13+ |
| 业务库 | PostgreSQL（Docker） | 16 |
| 对象存储 | MinIO（Docker） | latest（RELEASE） |
| 部署 | Docker / Docker Compose | Compose v2 |

### 1.1 阿里云百炼模型（2026-08 确认）
| 用途 | 模型 ID | 关键参数 |
|---|---|---|
| 对话生成 | `qwen-plus`（质量优先可 `qwen-max`） | 流式 SSE |
| 文本向量 | `text-embedding-v4` | **dimensions=1024**（OpenAI 兼容接口传 dimensions 生效） |
| 重排序 | `qwen3-rerank` | 开 `hybrid=true` 综合语义+BM25；输入可到 120K token |

- 接入前提：阿里云百炼控制台开通服务 → 创建 API Key（`sk-...`）。
- 环境变量：`DASHSCOPE_API_KEY`（**禁止硬编码进代码/仓库**，放 .env / 运行环境）。

---

## 2. 目录结构（Monorepo）

```text
rag-platform/
├── docker-compose.yml            # 全栈编排
├── .env.example                  # 环境变量模板
├── docs/
│   └── init.sql                  # 建表脚本（12 张表，见《建表 DDL》文档）
├── backend/
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/example/rag/
│       │   ├── RagApplication.java
│       │   ├── config/           # SecurityConfig, MybatisPlusConfig, QdrantConfig, RagModelConfig, MinioConfig
│       │   ├── security/         # JwtAuthenticationFilter, JwtUtil, LoginUser
│       │   ├── controller/       # Auth, User, Dept, Role, KnowledgeBase, Document, Rag, Audit Controller
│       │   ├── service/          # 业务 Service + RagPipeline + DocParseService + ChunkService
│       │   ├── mapper/           # MyBatis-Plus Mapper 接口
│       │   ├── entity/           # 与 12 张表对应实体
│       │   ├── dto/              # Req/Resp VO
│       │   └── rag/              # DashScopeReranker, PromptTemplates, Citation
│       └── resources/
│           └── application.yml
├── frontend/
│   ├── Dockerfile
│   ├── package.json
│   ├── vite.config.ts
│   ├── nginx.conf                # 反代 /api → backend
│   └── src/
│       ├── main.ts
│       ├── router/index.ts
│       ├── stores/auth.ts
│       ├── api/                  # auth.ts user.ts kb.ts rag.ts
│       ├── types/index.ts        # 全量 TS 类型
│       ├── utils/request.ts      # Axios 封装 + JWT 注入
│       ├── utils/sse.ts          # fetch 流式解析
│       ├── views/                # login/ layout/ dashboard/ kb/ chat/ system/ audit/
│       └── components/
└── README.md                     # 启动说明
```

---

## 3. 数据库设计

使用《建表 DDL》文档中的 `docs/init.sql`（12 张表：sys_dept/sys_user/sys_role/sys_permission/sys_user_role/sys_role_permission + kb_knowledge_base/kb_document/kb_chunk + kb_qa_record/kb_feedback/audit_log）。

后端启动任务：**首次启动时创建 Qdrant collection**（`kb_embeddings`，1024 维，Cosine，payload 索引 kb_id/dept_id/visibility）。向量不落 PG。

---

## 4. 后端实现方案（Spring Boot + LangChain4j）

### 4.1 pom.xml 关键依赖
```xml
<parent>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-parent</artifactId>
  <version>3.4.3</version>
</parent>

<properties>
  <java.version>17</java.version>
  <langchain4j.version>1.13.1</langchain4j.version>
</properties>

<dependencies>
  <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-web</artifactId></dependency>
  <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-security</artifactId></dependency>
  <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-validation</artifactId></dependency>
  <dependency><groupId>com.baomidou</groupId><artifactId>mybatis-plus-spring-boot3-starter</artifactId><version>3.5.7</version></dependency>
  <dependency><groupId>org.postgresql</groupId><artifactId>postgresql</artifactId></dependency>

  <dependency><groupId>dev.langchain4j</groupId><artifactId>langchain4j-open-ai</artifactId><version>${langchain4j.version}</version></dependency>
  <dependency><groupId>dev.langchain4j</groupId><artifactId>langchain4j-qdrant</artifactId><version>${langchain4j.version}</version></dependency>

  <dependency><groupId>io.jsonwebtoken</groupId><artifactId>jjwt-api</artifactId><version>0.12.6</version></dependency>
  <dependency><groupId>io.jsonwebtoken</groupId><artifactId>jjwt-impl</artifactId><version>0.12.6</version><scope>runtime</scope></dependency>
  <dependency><groupId>io.jsonwebtoken</groupId><artifactId>jjwt-jackson</artifactId><version>0.12.6</version><scope>runtime</scope></dependency>

  <dependency><groupId>org.apache.tika</groupId><artifactId>tika-parsers-standard-package</artifactId><version>2.9.0</version></dependency>
  <dependency><groupId>io.minio</groupId><artifactId>minio</artifactId><version>8.5.12</version></dependency>
  <dependency><groupId>org.projectlombok</groupId><artifactId>lombok</artifactId><optional>true</optional></dependency>
</dependencies>
```

### 4.2 application.yml（核心配置）
```yaml
server:
  port: 8080
spring:
  datasource:
    url: jdbc:postgresql://${PG_HOST:localhost}:5432/rag_platform
    username: ${PG_USER:rag}
    password: ${PG_PASSWORD:rag123456}
  servlet:
    multipart:
      max-file-size: 50MB
      max-request-size: 60MB
mybatis-plus:
  configuration:
    map-underscore-to-camel-case: true
  global-config:
    db-config:
      logic-delete-field: deleted      # 逻辑删除

qdrant:
  host: ${QDRANT_HOST:localhost}
  port: ${QDRANT_PORT:6334}
  collection: kb_embeddings

dashscope:
  api-key: ${DASHSCOPE_API_KEY}
  base-url: https://dashscope.aliyuncs.com/compatible-mode/v1
  chat-model: qwen-plus
  embedding-model: text-embedding-v4
  embedding-dimensions: 1024
  rerank-model: qwen3-rerank
  rerank-hybrid: true

minio:
  endpoint: ${MINIO_ENDPOINT:http://localhost:9000}
  access-key: ${MINIO_ACCESS_KEY:minioadmin}
  secret-key: ${MINIO_SECRET_KEY:minioadmin}
  bucket: rag-docs

rag:
  top-k: 20           # 向量召回数
  rerank-top-k: 5     # 精排后送入 LLM 数
  min-score: 0.30     # 召回最低分
  max-token-per-chunk: 512
  overlap: 64
```

### 4.3 RagModelConfig（DashScope Bean 装配）
```java
@Configuration
public class RagModelConfig {
    @Bean
    public ChatModel chatModel(@Value("${dashscope.base-url}") String base,
                               @Value("${dashscope.api-key}") String key,
                               @Value("${dashscope.chat-model}") String model) {
        return OpenAiChatModel.builder()
                .baseUrl(base).apiKey(key).modelName(model)
                .temperature(0.3d)
                .build();
    }

    @Bean
    public EmbeddingModel embeddingModel(@Value("${dashscope.base-url}") String base,
                                         @Value("${dashscope.api-key}") String key,
                                         @Value("${dashscope.embedding-model}") String model,
                                         @Value("${dashscope.embedding-dimensions}") int dim) {
        return OpenAiEmbeddingModel.builder()
                .baseUrl(base).apiKey(key).modelName(model)
                .dimensions(dim)          // text-embedding-v4 传 dimensions=1024
                .build();
    }

    @Bean
    public EmbeddingStore<TextSegment> embeddingStore(QdrantProperties props) {
        return QdrantEmbeddingStore.builder()
                .host(props.getHost()).port(props.getPort())
                .collectionName(props.getCollection())
                .build();
    }
    // 启动时校验/创建 collection（QdrantClient 创建 kb_embeddings: size=1024, Cosine，并建 payload 索引）
}
```

### 4.4 Qdrant collection 初始化
启动时用 QdrantClient 检查 `kb_embeddings`，不存在则创建（vector size=1024, distance=Cosine），并为 `kb_id / dept_id`(integer)、`visibility`(keyword) 建 payload 索引——这是**权限过滤性能的关键**。

### 4.5 文档处理链路（DocParseService + ChunkService）
```
上传 → MinIO 存原件 → 异步任务：
  1) Tika 解析提取纯文本（带页数）
  2) 分块：按段落/标题切分，maxToken=512、overlap=64，中文按字符不按词
  3) 每个 chunk 打 metadata：{doc_id, kb_id, page_no, visibility, dept_id}
  4) embeddingModel.embed(text) → QdrantEmbeddingStore.add(pointId=chunk.id, textSegment含metadata)
  5) 更新 kb_document: parse_status=SUCCESS, chunk_status=INDEXED, chunk_count
```
- 分块实现用 LangChain4j `DocumentSplitter`（`DocumentByParagraphSplitter` 或自定义按标题层级），或直接用 LangChain4j `TextSegmentTransformer`。
- 异常时置 FAILED + error_msg，支持 `/reindex` 重跑。
- 队列用 `@Async` + 简单内存队列即可（MVP），生产可换 Redis/线程池。

### 4.6 RAG 问答流水线（RagPipeline，核心）
```java
@Service
public class RagPipeline {
    // 1. 权限过滤：由业务层算用户可见 kb_id 集合（owner/ORG/PUBLIC/本部门DEPT）
    //    转成 Qdrant Filter（must: kb_id in [...]; should: visibility=PUBLIC 或 dept_id=用户部门）
    public RagAnswer chat(String question, Long userId, boolean stream, SseEmitter emitter) {
        List<Long> allowedKbIds = kbService.listAllowedKbIds(userId);
        Filter filter = buildKbFilter(allowedKbIds, userDeptId);

        // 2. 向量召回 TopK=20（EmbeddingStoreContentRetriever 或直接 embeddingStore.findRelevant）
        List<RetrievedChunk> candidates = retrieve(question, filter, ragProps.getTopK());

        // 3. Rerank 精排 Top5（DashScopeReranker → qwen3-rerank hybrid）
        List<RetrievedChunk> top = dashScopeReranker.rerank(question, candidates, ragProps.getRerankTopK());

        // 4. 组装 Prompt（system: 仅依据上下文回答+编号引用；user: context + question）
        String prompt = PromptTemplates.build(question, top);

        // 5. LLM 流式生成（OpenAiChatModel.stream() → Flux<String> → SseEmitter 逐条 send）
        //    同时收集全文用于落库

        // 6. 引用溯源：top 中每个 chunk 的 metadata 出 citations（docId, fileName, pageNo, snippet）
        // 7. 审计落库：kb_qa_record（question/answer/retrieved_chunk_ids/rerank_scores/model/latency）
        return answer;
    }
}
```

### 4.7 DashScopeReranker（自定义实现 LangChain4j Reranker）
- 调用百炼重排接口：`POST https://dashscope.aliyuncs.com/compatible-mode/v1/rerank`
  body: `{"model":"qwen3-rerank","query":question,"documents":[candidate文本...],"parameters":{"hybrid":true}}`
- 解析 `results[].relevance_score`，按分数降序取 Top-N，返回 `List<RetrievedAugmentation>`。
- 用 RestClient/WebClient 实现，可加超时与重试。

### 4.8 认证（Spring Security + JWT）
- `JwtUtil`：签发/校验 HS256 token（jjwt 0.12），过期 24h。
- `JwtAuthenticationFilter`：解析 `Authorization: Bearer` → 载入 LoginUser（含 deptId、权限点）→ 注入 SecurityContext。
- `SecurityConfig`：放行 `/api/auth/login`、静态资源；其余需认证。权限点用 `@PreAuthorize("hasAuthority('kb:doc:upload')")` 标注在 Controller 方法上。

### 4.9 SSE 流式问答 Controller
```java
@PostMapping(value = "/api/rag/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public SseEmitter chat(@RequestBody RagChatReq req, @AuthenticationPrincipal LoginUser user) {
    SseEmitter emitter = new SseEmitter(0L); // 不超时
    // 异步线程执行 ragPipeline.chat(...)，内部：
    //   emitter.send(SseEmitter.event().name("citations").data(citations))
    //   emitter.send(SseEmitter.event().name("chunk").data(new DeltaDto(text)))
    //   结束后 emitter.send(event "done").data(recordId) + emitter.complete()
    return emitter;
}
```

### 4.10 接口清单（后端实现依据）
见《建表 DDL》文档第二部分的完整 REST 清单（Auth/User/Dept/Role/KnowledgeBase/Document/Rag/Audit 四组，含方法、路径、权限标识）。以下为必须实现的 RAG 核心接口：
- `POST /api/rag/chat`（SSE）· `GET /api/rag/records` · `GET /api/rag/records/{id}` · `POST /api/rag/feedback` · `GET /api/rag/chunks/{id}`
- `POST /api/kb/bases/{id}/docs`（multipart 上传）· `GET /api/kb/docs/{docId}` · `POST /api/kb/docs/{docId}/reindex`

---

## 5. 前端实现方案（Vue3 + TS）

### 5.1 页面与路由
| 路由 | 页面 | 功能 |
|---|---|---|
| `/login` | 登录页 | 登录表单 → auth store → 跳转 |
| `/` | 主布局 | 侧边栏(菜单按权限)+顶栏(用户/退出) |
| `/dashboard` | 工作台 | 统计卡片（知识库数/文档数/今日问答） |
| `/kb` | 知识库列表 | 卡片/表格 + 新建/编辑/删除 + 可见范围设置 |
| `/kb/:id` | 知识库详情 | 文档列表、上传(拖拽)、解析状态、删除、重新索引 |
| `/chat` | RAG 问答 | 对话界面：消息列表、流式输出、引用卡片(点击跳原文)、点赞/点踩 |
| `/system/users` | 用户管理 | CRUD + 分配角色 |
| `/system/depts` | 部门管理 | 树形 CRUD |
| `/system/roles` | 角色管理 | CRUD + 分配权限点 |
| `/audit` | 审计日志 | 表格 + 条件筛选 |

### 5.2 核心类型（types/index.ts 节选）
```ts
export interface KnowledgeBase { id:number; name:string; description?:string;
  visibility:'PRIVATE'|'DEPT'|'ORG'|'PUBLIC'; deptId?:number;
  chunkSize:number; chunkOverlap:number; createdAt:string; docCount?:number }
export interface DocumentItem { id:number; kbId:number; fileName:string; fileType:string;
  fileSize:number; version:number; parseStatus:'PENDING'|'PARSING'|'SUCCESS'|'FAILED';
  chunkStatus:'PENDING'|'INDEXING'|'INDEXED'|'PARTIAL'|'FAILED'; chunkCount:number; errorMsg?:string }
export interface Citation { chunkId:number; docId:number; fileName:string; pageNo?:number; snippet:string; kbId:number }
export interface RagRequest { question:string; kbIds?:number[]; stream?:boolean; topK?:number }
export interface RagResponse { recordId:number; answer:string; citations:Citation[]; latencyMs:number }
```

### 5.3 SSE 流式客户端（utils/sse.ts 核心）
```ts
export async function streamChat(payload: RagRequest, handlers: {
  onCitations:(c:Citation[])=>void; onDelta:(t:string)=>void; onDone:(id:number)=>void; onError:(e:Error)=>void;
}) {
  const res = await fetch('/api/rag/chat', {
    method:'POST', headers:{'Content-Type':'application/json', Authorization:`Bearer ${token}`},
    body: JSON.stringify(payload),
  });
  const reader = res.body!.getReader();
  const decoder = new TextDecoder();
  let buf = '';
  while (true) {
    const {done, value} = await reader.read();
    if (done) break;
    buf += decoder.decode(value, {stream:true});
    const events = buf.split('\n\n'); buf = events.pop()!;
    for (const ev of events) {
      const name = /^event: (.+)$/m.exec(ev)?.[1];
      const data = /^data: (.+)$/m.exec(ev)?.[1];
      if (!name || !data) continue;
      if (name==='citations') handlers.onCitations(JSON.parse(data));
      else if (name==='chunk') handlers.onDelta(JSON.parse(data).delta);
      else if (name==='done') handlers.onDone(JSON.parse(data).recordId);
    }
  }
}
```

### 5.4 Axios 封装（utils/request.ts）
- 请求拦截：注入 `Authorization: Bearer`；响应拦截：401 跳登录、统一错误 ElMessage。
- `vite.config.ts` 配 dev proxy：`/api → http://localhost:8080`。

---

## 6. Docker 部署方案

### 6.1 docker-compose.yml
```yaml
services:
  postgres:
    image: postgres:16
    environment:
      POSTGRES_DB: rag_platform
      POSTGRES_USER: rag
      POSTGRES_PASSWORD: rag123456
    volumes:
      - pgdata:/var/lib/postgresql/data
      - ./docs/init.sql:/docker-entrypoint-initdb.d/init.sql:ro   # 首启自动建表
    healthcheck: { test: ["CMD-SHELL","pg_isready -U rag"], interval: 5s, timeout: 3s, retries: 10 }

  qdrant:
    image: qdrant/qdrant:latest
    volumes: [ qdrantdata:/qdrant/storage ]
    ports: [ "6333:6333", "6334:6334" ]

  minio:
    image: minio/minio:latest
    command: server /data --console-address ":9001"
    environment:
      MINIO_ROOT_USER: minioadmin
      MINIO_ROOT_PASSWORD: minioadmin
    volumes: [ miniodata:/data ]
    ports: [ "9000:9000", "9001:9001" ]

  backend:
    build: ./backend
    environment:
      PG_HOST: postgres
      QDRANT_HOST: qdrant
      MINIO_ENDPOINT: http://minio:9000
      DASHSCOPE_API_KEY: ${DASHSCOPE_API_KEY}
    depends_on:
      postgres: { condition: service_healthy }
      qdrant: { condition: service_started }
      minio: { condition: service_started }
    ports: [ "8080:8080" ]

  frontend:
    build: ./frontend
    depends_on: [ backend ]
    ports: [ "80:80" ]

volumes:
  pgdata: {}
  qdrantdata: {}
  miniodata: {}
```
`.env.example`：`DASHSCOPE_API_KEY=sk-xxx`（本地 `cp .env.example .env` 后填真实 Key）。

### 6.2 Dockerfile
- **backend**：multi-stage —— `maven:3.9-eclipse-temurin-17` 构建 → `eclipse-temurin:17-jre` 运行 `java -jar`。
- **frontend**：multi-stage —— `node:20-alpine` 构建 `npm run build` → `nginx:alpine`，`nginx.conf` 中 `location /api { proxy_pass http://backend:8080; }`。

---

## 7. 实现步骤与任务拆解（Codex 执行顺序）

> 每个 Phase 完成并自测通过后再进入下一个。给 Codex 的指令建议：粘贴对应 Phase 全文 + 上一 Phase 产物，要求「按本文档实现，不引入未指定的依赖」。

### Phase 0 · 脚手架（0.5 天）
- 建 monorepo 目录结构；`docs/init.sql` 放入。
- 后端：`spring init` 生成 Spring Boot 3.4.3 工程，pom 加齐依赖。
- 前端：`npm create vite@latest`（vue-ts 模板）+ 装 element-plus/pinia/router/axios。
- **验收**：后端 `/actuator/health` 可访问；前端 `npm run dev` 出默认页；`docker compose up postgres qdrant minio` 三个容器健康。

### Phase 1 · Docker 基础设施
- 写全 `docker-compose.yml` + 两个 Dockerfile + nginx.conf + .env.example。
- postgres 首启执行 init.sql 建 12 表。
- **验收**：`psql` 连上看到 12 张表；Qdrant API `GET /collections` 可访问；MinIO 控制台可登录。

### Phase 2 · 后端骨架 + 认证
- 实体/Mapper（12 表）、JWT 工具与过滤器、SecurityConfig、`/api/auth/login`、`/api/auth/me`。
- 用户/部门/角色 CRUD 接口 + 权限点注解。
- **验收**：登录拿 token；无 token 访问 401；admin 可建用户/角色并分配权限。

### Phase 3 · 知识库管理 + 文档处理链路
- 知识库 CRUD（含可见范围）；文档上传→MinIO→Tika 解析→分块→向量化→入 Qdrant（异步）。
- `GET /api/kb/docs/{id}` 返回解析/索引进度；`/reindex` 支持失败重跑。
- **验收**：上传一份 PDF，状态依次 PENDING→PARSING→INDEXED，Qdrant collection 内能看到 chunk 且 payload 含 kb_id/page_no；重传同文件生成新版本。

### Phase 4 · RAG 问答（核心）
- RagModelConfig（Chat/Embedding/Store Bean）；collection 自动创建。
- RagPipeline（权限过滤→召回→rerank→prompt→流式生成→引用→审计）+
- DashScopeReranker（qwen3-rerank hybrid）+ `POST /api/rag/chat`(SSE) + 历史/反馈/溯源接口。
- **验收**：向已入库文档提问能流式返回答案 + 引用卡片；问无权知识库内容不返回；`kb_qa_record` 有完整留痕；`/api/rag/feedback` 生效。

### Phase 5 · 前端
- 布局/路由守卫/Pinia auth；登录页；知识库列表+详情+上传；RAG 对话页（SSE 流式+引用+反馈）；用户/部门/角色管理；审计页。
- **验收**：全流程 UI 走通；对话流式打字机效果；引用可点击；401 自动跳登录。

### Phase 6 · 联调与部署
- `docker compose up -d --build` 全栈启动；nginx 反代验证；接口冒烟清单全过。
- README 写启动/配置/常见问题。
- **验收**：新机器 `cp .env.example .env` + 填 Key + `docker compose up -d` 即可用；核心链路（登录→传文档→问答）全通。

---

## 8. 验收与测试要点

| 项 | 验收标准 |
|---|---|
| 功能冒烟 | 登录→建知识库→传文档→索引→问答→反馈 全链路 30 分钟内走通 |
| 权限隔离 | 用户 A 无权知识库：接口不返回、问答不泄露（用 Qdrant filter 验证） |
| 流式 | 对话为打字机式增量返回，无整包等待；断网不挂死 |
| 引用可信 | 答案每段可追溯 citations（docId/页码/原文片段），点击可定位 |
| 数据合规 | `DASHSCOPE_API_KEY` 仅存 .env/环境变量，代码与 git 无明文 |
| 文档边界 | 上传 50MB 上限、非法类型拒绝、解析失败有 error_msg 可重试 |
| 质量基线 | 用 20~50 条真实问答做评测集，命中率(Recall@5)≥0.8、答案相关率≥0.85 后再上线推广 |

---

## 9. 风险与注意事项

1. **模型成本**：qwen-plus + text-embedding-v4 走 API 按量计费；先设百炼控制台用量告警；Embedding 离线批量做、索引复用，避免重复调用。
2. **检索质量**：分块粒度与 overlap 直接决定效果；上线后用反馈数据迭代 Prompt 与分块参数。
3. **权限底线**：权限过滤必须在检索阶段（Qdrant filter）完成，绝不能只靠 Prompt 提示「无权不答」。
4. **扫描件**：Tika 对扫描版 PDF 提取为空——后续可加 PaddleOCR 服务（独立容器）增强。
5. **版本锁定**：LangChain4j 1.13.1 / Spring Boot 3.4.3 为已验证组合；升级大版本前先在分支验证兼容性。
6. **密钥安全**：MinIO/Qdrant/Postgres 生产环境必须改默认口令；Qdrant 开启 API Key。

---

*文档版本 v1.0 · 生成于 2026-08-29 · 配套《建表 DDL》文档（init.sql 12 表）*
