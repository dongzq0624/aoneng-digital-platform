# RAG 聊天流式输出 — 企业主流架构方案（可推翻现有实现）

> 基于当前「模型原生 SSE \+ 后端即时转发 \+ 前端节流渲染」链路，给出**可推翻现有架构**的企业级主流方案。覆盖协议标准化、后端响应式重构、流式编排引擎、模型网关、前端增量渲染、企业级能力六大维度，可直接交付落地。
> 
> 

---

## 一、现有架构核心问题诊断

|维度|现状|企业级差距|
|---|---|---|
|协议|自定义 `event: chunk` \+ `citations` \+ `done`|非 OpenAI 兼容，前端/第三方接入成本高，生态工具不可复用|
|后端模型|有界线程池 \+ 阻塞 IO \+ 手动 SSE 解析|并发上限低、线程泄漏风险、无背压、资源利用率差|
|检索链路|Controller 内同步调用 Milvus，串行阻塞|检索耗时直接叠加为首字延迟，无法异步化|
|模型接入|硬编码 DashScope，单模型|无多模型路由、无降级、无统一计费、切换模型需改代码|
|上下文管理|每次请求内组装，无独立存储|多轮对话上下文不可控、无持久化、无法跨会话复用|
|前端渲染|全量 Markdown 重渲染 \+ 18ms 固定节流|长文本卡顿、无增量解析、无虚拟滚动、断连无恢复|
|可观测|空白日志|无全链路 Trace、无 SLA 指标、无告警|

---

## 二、目标架构总览

```
┌─────────────────────────────────────────────────────────────┐
│                        前端 (Vue/React)                      │
│  useChat Hook · 流式Markdown增量解析 · 虚拟滚动 · 自动重连    │
└──────────────────────────┬──────────────────────────────────┘
                           │ SSE (OpenAI 兼容协议)
┌──────────────────────────▼──────────────────────────────────┐
│                      API 网关层 (Gateway)                     │
│        JWT 鉴权 · 限流 · 路由 · 审计日志 · 请求校验            │
└──────────────────────────┬──────────────────────────────────┘
                           │
┌──────────────────────────▼──────────────────────────────────┐
│                  流式编排引擎 (Orchestrator)                  │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────────┐ │
│  │ 检索流水线 │→│ Rerank   │→│ 上下文组装 │→│ 模型流式调用   │ │
│  │ (异步)    │  │ (精排)   │  │ (Token)  │  │ (背压控制)    │ │
│  └──────────┘  └──────────┘  └──────────┘  └──────────────┘ │
│         全链路 Reactive 流式 · 事件驱动 · 可中断可恢复         │
└──────┬───────────────┬───────────────┬──────────────────────┘
       │               │               │
┌──────▼──────┐ ┌──────▼──────┐ ┌──────▼──────────┐
│ 向量检索服务 │ │ 上下文管理   │ │ 模型网关         │
│ Milvus      │ │ 会话存储     │ │ 多模型路由/降级  │
│ 多路召回    │ │ 历史消息     │ │ 计费/熔断/缓存   │
│ 结果缓存    │ │ 持久化       │ │ DashScope/其他   │
└─────────────┘ └─────────────┘ └─────────────────┘
```

---

## 三、协议层：OpenAI 兼容 SSE 标准（P0 强制）

### 3\.1 推翻自定义事件，统一 OpenAI 兼容格式

**现有自定义事件**：`chunk` / `citations` / `done` / `error` → **废弃**

**新标准事件流**（与 OpenAI Chat Completions SSE 完全对齐）：

```
event: message
data: {"id":"chatcmpl-xxx","object":"chat.completion.chunk","created":1710000000,"model":"qwen-plus","choices":[{"index":0,"delta":{"role":"assistant","content":""},"finish_reason":null}]}

data: {"choices":[{"delta":{"content":"根据"},"finish_reason":null}]}

data: {"choices":[{"delta":{"content":"知识库"},"finish_reason":null}]}

data: [DONE]
```

### 3\.2 扩展事件（RAG 专属，通过 `delta` 扩展字段承载）

|扩展字段|时机|说明|
|---|---|---|
|`delta.sources`|检索完成后、正文开始前|溯源文档列表，含 docId、docName、pageNo、chunkId、score|
|`delta.citations`|正文流式中按需插入|内联引用标记 `[1]` 与 sources 映射|
|`delta.tool_calls`|Agent 模式|工具调用流式输出|
|`delta.thinking`|推理模型|思考过程（可折叠展示）|
|`delta.usage`|结束时|prompt\_tokens / completion\_tokens / total\_tokens|

### 3\.3 事件时序（强制不可逆）

```
连接建立 → 首条 message(role占位) → sources(溯源) → 持续 content(delta) → usage → [DONE]
                                                          ↓
                                                       error(异常中断)
```

**收益**：前端可直接复用 Vercel AI SDK / 开源 chat\-ui 生态，第三方接入零成本，模型切换协议不变。

---

## 四、后端架构重构（P0 核心）

### 4\.1 线程池 \+ 阻塞 IO → 响应式编程（Reactive）

**现有**：有界线程池处理问答任务，手动解析 SSE 逐行转发。

**重构为**：Spring WebFlux（Reactor）或 Quarkus/Vert\.x，全链路非阻塞。

|项|旧方案|新方案|
|---|---|---|
|并发模型|线程池（1请求1线程）|EventLoop（少量线程处理万级并发）|
|IO 模型|阻塞 HTTP 客户端|非阻塞 WebClient / HttpClient|
|背压|无|Reactor Flux 原生背压，上游快时自动缓冲/丢弃|
|取消传播|手动设置取消标记|订阅取消自动传播到上游 HTTP 请求|
|异常处理|try\-catch 散落|onErrorResume / retry 统一声明式处理|

**核心代码结构（伪代码）**：

```java
@PostMapping(value = "/api/rag/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public Flux<ServerSentEvent<ChatChunk>> streamChat(@RequestBody ChatRequest req) {
    return authService.verify(req.getToken())
        .then(retrievalService.search(req.getQuery(), req.getConversationId()))
        .flatMapMany(ctx -> modelGateway.streamChat(ctx)
            .onBackpressureBuffer(50)
            .doOnCancel(() -> log.info("client disconnected"))
            .timeout(Duration.ofSeconds(60)));
}
```

### 4\.2 检索与生成解耦：异步流水线（Streaming RAG）

**现有致命问题**：权限→检索→上下文→模型，全串行，检索 300\~800ms 完全叠加为首字延迟。

**企业主流方案：检索预热 \+ 生成流水线异步化**

```
时间轴：
T0  请求到达 → 鉴权通过 → 立即返回 SSE 响应头（200 + content-type:text/event-stream）
T0  并行触发：检索流水线（异步） + 模型连接预热（异步）
T1  检索完成 → 推送 sources 事件 → 组装上下文 → 发起模型流式请求
T2  模型首字到达 → 推送 content delta
```

**关键实现**：

- 鉴权通过后 `return Flux.create(sink -> {...})`，先建立 SSE 连接再做后续工作。

- 检索用 `Mono.fromCallable(...)` 调度到独立弹性线程池（BoundedElastic），不阻塞 EventLoop。

- 检索结果通过 `sink.next(sourcesEvent)` 推送，然后 `flatMap` 到模型流式 Flux。

- 若检索超时（可配置 3s），降级为无上下文直接回答，保证可用性。

### 4\.3 模型网关层（Model Gateway）

**现有**：DashScopeService 硬编码，直接调用 DashScope。

**重构为独立模型网关**，统一接入层：

```
┌─────────────────────────────────────────┐
│              Model Gateway               │
├─────────────────────────────────────────┤
│  路由层：按模型名/成本/延迟路由到具体提供商  │
│  适配层：DashScope / OpenAI / 私有模型     │
│  熔断层：CircuitBreaker（失败率阈值降级）   │
│  降级层：主模型不可用 → 自动切备用模型      │
│  缓存层：相同 query + 上下文 → 缓存命中     │
│  计费层：Token 计量、预算控制、配额管理     │
│  观测层：延迟、成功率、Token 消耗统计       │
└─────────────────────────────────────────┘
```

**核心能力**：

- **统一接口**：`modelGateway.streamChat(ChatRequest) → Flux<ChatChunk>`，上层不感知具体提供商。

- **多模型路由**：按知识库类型/用户等级/成本策略选择模型（如简单问题用小模型，复杂推理用大模型）。

- **自动降级**：DashScope 限流/超时 → 自动切换备用模型（如本地部署模型），前端无感知。

- **语义缓存**：对高频重复问题（如"公司年假政策"），缓存完整回答，命中后直接流式回放缓存内容，首字延迟 \< 50ms。

### 4\.4 上下文管理服务（独立模块）

**现有**：每次请求内从 conversationId 查历史、组装 prompt，无持久化。

**重构为独立上下文管理服务**：

|能力|说明|
|---|---|
|会话存储|Redis \+ DB 双层，会话消息持久化，支持跨服务恢复|
|上下文窗口管理|自动裁剪历史消息，保留 system \+ 最近 N 轮 \+ 检索结果，Token 精准计算|
|摘要压缩|长对话自动生成滚动摘要（rolling summary），替代早期历史|
|检索结果缓存|同会话相似 query 复用检索结果（语义相似度阈值 0\.92）|
|并发安全|同会话串行化，避免多请求乱序|

**Token 截断策略**：

- 按模型上限（如 qwen\-plus 32K）预留 20% 给生成。

- 优先级：system prompt \> 检索结果（按相似度降序保留）\> 最近对话 \> 早期对话（压缩为摘要）。

- 每个 chunk 保留 docId/docName/pageNo，截断不丢失溯源。

---

## 五、前端架构重构（P1 体验质变）

### 5\.1 全量重渲染 → 流式增量 Markdown 解析

**现有**：每 18ms 合并队列，全量字符串传给 Markdown 组件重新渲染。

**问题**：长文本（\>2000字）时每次全量解析，DOM 全量 diff，卡顿明显；代码块、表格未闭合时渲染闪烁。

**企业主流方案：增量解析 \+ 分块渲染**

```
接收 delta → 追加到缓冲区 → 按"完整语法单元"切分（段落/列表/代码块/表格）
                         → 已闭合单元立即渲染（不重渲染已有内容）
                         → 未闭合单元保留在缓冲区等待
```

**实现要点**：

- 使用 `markdown-it` \+ 自定义增量规则，或 `react-markdown` 的 `components` 缓存机制。

- 代码块检测到 ````` 开始后，缓冲直到匹配结束 ````` 再一次性渲染，避免中间态闪烁。

- 表格同理，检测到表头分隔行 `|---|` 后缓冲直到空行。

- Vue 场景：用 `v-html` 增量拼接已闭合 HTML 片段，而非响应式全量替换。

### 5\.2 固定节流 → 自适应帧率 \+ 帧合并

```javascript
// 自适应渲染引擎
class StreamRenderer {
  constructor() {
    this.buffer = '';
    this.lastFrameTime = 0;
    this.targetFPS = 60;        // 目标帧率
    this.minInterval = 16;      // 最小间隔(ms)
    this.maxInterval = 100;     // 最大间隔(ms)，低速时避免闪烁
  }

  push(delta) {
    this.buffer += delta;
    this.scheduleRender();
  }

  scheduleRender() {
    const now = performance.now();
    const elapsed = now - this.lastFrameTime;
    // 根据缓冲区大小动态决定渲染时机
    const bufferSize = this.buffer.length;
    const interval = bufferSize > 100 ? this.minInterval : 
                     bufferSize > 20 ? 32 : this.maxInterval;

    if (elapsed >= interval) {
      this.flush();
    } else {
      clearTimeout(this.timer);
      this.timer = setTimeout(() => this.flush(), interval - elapsed);
    }
  }

  flush() {
    this.lastFrameTime = performance.now();
    const closed = this.extractClosedUnits(this.buffer);
    this.appendToDOM(closed.html);
    this.buffer = closed.remaining;
  }
}
```

### 5\.3 断连自动重连 \+ 断点续传

**现有**：SSE 断连直接结束，无恢复。

**企业方案**：

- 使用 `@microsoft/fetch-event-source` 替代原生 EventSource（支持 POST、自定义 header、JWT）。

- 断连时自动重连（指数退避：1s → 2s → 4s，最多 3 次）。

- 重连请求携带 `lastEventId`（已接收的最后一个 chunk 序号），后端从断点继续推送，不重复不丢失。

- 前端缓存已输出文本，重连后拼接续传内容。

### 5\.4 长对话虚拟滚动

- 消息列表使用虚拟滚动（Vue：`vue-virtual-scroller`；React：`react-window`）。

- 单条消息内超过 5000 字时，内容区域启用虚拟滚动或折叠（"展开全文"）。

- 历史消息懒加载，首屏只渲染最近 20 条。

---

## 六、企业级能力（P1\~P2）

### 6\.1 内容安全流式中间过滤

- 模型输出的 delta 经过**敏感词实时检测**后再转发前端。

- 命中敏感内容：立即截断当前输出，替换为合规提示，记录审计日志。

- 输入侧：用户 query 先过内容安全，违规直接拒绝，不进入检索。

### 6\.2 审计与合规

- 全量记录：query、检索结果、模型输入输出、Token 消耗、用户标识、时间戳。

- 支持按会话/用户/时间范围导出，满足等保/行业合规要求。

- 敏感数据脱敏（手机号、身份证号）后存储。

### 6\.3 成本控制

|机制|说明|
|---|---|
|Token 预算|按用户/部门设置日/月 Token 上限，超额降级或拒绝|
|模型分级|简单查询路由到小模型（成本低 10 倍），复杂问题用大模型|
|语义缓存|高频问题缓存命中，零模型调用成本|
|上下文裁剪|精准 Token 计算，避免无谓的长上下文浪费|

### 6\.4 A/B 测试

- 按用户百分比分流到不同模型/提示词/检索策略。

- 指标：回答采纳率、用户满意度、首字延迟、Token 消耗。

- 数据驱动迭代，而非凭感觉调参。

---

## 七、可观测体系（P2）

### 7\.1 OpenTelemetry 全链路追踪

```
用户请求 → Gateway(span) → 鉴权(span) → 检索(span: Milvus查询) → Rerank(span) → 模型调用(span: 首字延迟/总延迟) → 前端渲染(span)
```

- 每个请求携带 `traceId`，贯穿前后端，日志关联。

- 慢请求自动采样完整链路，定位瓶颈。

### 7\.2 核心 SLA 指标

|指标|目标值|告警阈值|
|---|---|---|
|首字延迟（TTFT）|P50 \< 300ms，P95 \< 800ms|P95 \> 1\.5s|
|检索耗时|P50 \< 150ms，P95 \< 400ms|P95 \> 600ms|
|模型 Token 吞吐|\> 30 tokens/s|\< 15 tokens/s|
|SSE 连接成功率|\> 99\.5%|\< 99%|
|断连率|\< 1%|\> 3%|
|回答满意度（点赞率）|\> 85%|\< 70%|

### 7\.3 结构化日志

- JSON 格式日志，包含 traceId、conversationId、userId、模型、Token、耗时、错误码。

- 接入 ELK / Loki，支持按维度聚合查询。

---

## 八、落地优先级与排期

### P0（第 1\~2 周，架构质变，必上线）

|项|交付物|
|---|---|
|协议标准化|OpenAI 兼容 SSE 事件格式，前后端联调|
|后端响应式改造|WebFlux 重构 Controller \+ Service，非阻塞 HTTP 客户端|
|检索异步化|SSE 连接建立后并行检索，首字延迟压缩至 300ms 内|
|模型网关|抽离 ModelGateway 接口，DashScope 作为首个实现，预留多模型扩展|
|取消传播|客户端断开自动终止上游模型请求，杜绝无效计费|

### P1（第 3\~4 周，体验升级）

|项|交付物|
|---|---|
|前端增量渲染|流式 Markdown 增量解析引擎，替换全量重渲染|
|自适应帧率|动态渲染间隔，消除卡顿和闪烁|
|自动重连|fetch\-event\-source \+ 断点续传|
|上下文管理服务|独立模块，Token 精准截断 \+ 滚动摘要|
|溯源前置|sources 事件在正文前推送，前端优先展示引用列表|

### P2（第 5\~8 周，企业级能力）

|项|交付物|
|---|---|
|多模型路由 \+ 降级|主备模型自动切换，语义缓存|
|内容安全过滤|输入输出双向敏感词检测|
|审计日志|全量会话记录 \+ 导出|
|成本控制|Token 预算 \+ 模型分级路由|
|可观测|OpenTelemetry 链路 \+ Grafana 看板 \+ 告警|
|长对话优化|虚拟滚动 \+ 历史消息懒加载|

---

## 九、技术选型建议

|层|推荐选型|说明|
|---|---|---|
|后端框架|Spring Boot 3 \+ WebFlux（Reactor）|企业主流，生态成熟，与现有 Spring 体系兼容|
|非阻塞 HTTP|Spring WebClient / Reactor Netty|原生支持 SSE 流式响应|
|向量库|Milvus|已迁移，使用 Milvus Java SDK 管理集合、索引、写入与检索|
|缓存|Redis（会话 \+ 检索结果 \+ 语义缓存）|企业标配|
|模型网关|自研轻量网关 / 开源 One API / LiteLLM|中小团队推荐 One API，开箱即用多模型管理|
|前端 SSE|@microsoft/fetch\-event\-source|支持 POST \+ 自定义 header，替代原生 EventSource|
|前端 Markdown|markdown\-it \+ 自定义增量规则 / react\-markdown|可控性强，支持增量|
|可观测|OpenTelemetry \+ Prometheus \+ Grafana \+ Loki|云原生标准栈|
|内容安全|阿里云内容安全 / 腾讯云天御 / 自建敏感词|按需选型|

---

## 十、一句话总结

**推翻现有「Controller 内串行 \+ 自定义 SSE \+ 全量重渲染」架构，升级为「OpenAI 兼容协议 \+ WebFlux 响应式流式编排 \+ 模型网关 \+ 前端增量渲染」的企业级标准架构**。P0 两周内完成协议标准化和响应式改造，首字延迟从 800ms\+ 压缩至 300ms 内；P1 完成前端体验和上下文管理；P2 补齐多模型、安全、审计、可观测等企业级能力。

> （注：部分内容可能由 AI 生成）
