# 企业员工数字化平台

按实施方案提供 Vue3 + TypeScript 前端、Spring Boot 后端、PostgreSQL/Qdrant/MinIO Docker 编排。

## Phase 实施状态

- Phase 0：Monorepo、Vue/TS、Spring Boot、SQL 脚手架完成。
- Phase 1：PostgreSQL、Qdrant、MinIO、前后端 Docker Compose 编排完成。
- Phase 2：JWT 登录、鉴权过滤器、用户/部门/角色基础接口完成。
- Phase 3：知识库 CRUD、文档上传校验、异步解析/索引状态、重索引接口完成；Tika/MinIO/Qdrant 适配点已保留。
- Phase 4：RAG SSE、引用、问答记录、反馈、chunk 溯源接口契约完成；DashScope 实际调用可在 `RagController` 中替换演示生成器。
- Phase 5：登录、工作台、知识库、文档详情、智能问答、用户权限、审计页面与路由守卫完成。
- Phase 6：前端构建、后端 Maven 打包、Compose 配置校验完成。

## 本地开发

```bash
cd frontend && npm install && npm run dev
```

前端开发地址：`http://localhost:5173`，演示账号 `admin / 123456`。

## 全栈部署

复制 `.env.example` 为 `.env` 并填入 `DASHSCOPE_API_KEY`，执行 `docker compose up -d --build`。
