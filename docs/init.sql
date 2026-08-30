-- =====================================================================

-- 默认组织架构（可重复执行，已有同 ID 部门时保留现有名称）
/* INSERT INTO sys_dept (id, parent_id, name, ancestors, sort, status) VALUES
    (1, 0, '总经办', '0,', 10, 1),
    (2, 0, '技术支持中心', '0,', 20, 1),
    (3, 0, '营销中心', '0,', 30, 1),
    (4, 0, '研发中心', '0,', 40, 1),
    (5, 0, '制造中心', '0,', 50, 1),
    (6, 0, '海外销售部', '0,', 60, 1),
    (7, 0, '职能中心', '0,', 70, 1),
    (8, 0, '工艺品质中心', '0,', 80, 1)
ON CONFLICT (id) DO NOTHING; */

-- 企业员工数字化平台 · 数据库初始化脚本 (PostgreSQL 15+)
-- 说明：
--   1) 业务结构化数据存本库；向量数据与向量检索存 Qdrant（见文末说明）。
--   2) 若采用 pgvector 起步方案，向量可直接存本库 kb_embedding 表（见文末备选）。
--   3) 建议执行：psql -h <host> -U <user> -d <db> -f init.sql
-- =====================================================================

-- 可选扩展（pgcrypto 提供 gen_random_uuid 等）
-- CREATE EXTENSION IF NOT EXISTS "pgcrypto";
-- 若采用 pgvector 方案，取消下一行注释
-- CREATE EXTENSION IF NOT EXISTS vector;

-- ============================ 一、用户与权限域 ============================

-- 部门表
CREATE TABLE sys_dept (
    id          BIGSERIAL PRIMARY KEY,
    parent_id   BIGINT       NOT NULL DEFAULT 0,
    name        VARCHAR(64)  NOT NULL,
    ancestors   VARCHAR(512) NOT NULL DEFAULT '',  -- 祖先部门ID链，如 "0,1,5,"
    sort        INT          NOT NULL DEFAULT 0,
    status      SMALLINT     NOT NULL DEFAULT 1,   -- 1 启用 0 禁用
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
COMMENT ON TABLE  sys_dept IS '部门表';
COMMENT ON COLUMN sys_dept.ancestors IS '祖先部门ID链，逗号分隔，用于权限继承与子树查询';
CREATE INDEX idx_dept_parent ON sys_dept(parent_id);

-- 用户表
CREATE TABLE sys_user (
    id            BIGSERIAL PRIMARY KEY,
    employee_no   VARCHAR(32)  NOT NULL,
    username      VARCHAR(64)  NOT NULL,
    password_hash VARCHAR(128) NOT NULL,          -- BCrypt
    real_name     VARCHAR(64)  NOT NULL,
    email         VARCHAR(128),
    phone         VARCHAR(32),
    dept_id       BIGINT,                          -- 所属部门
    avatar        VARCHAR(255),
    status        SMALLINT     NOT NULL DEFAULT 1, -- 1 启用 0 禁用
    last_login_at TIMESTAMPTZ,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted       BOOLEAN      NOT NULL DEFAULT FALSE
);
COMMENT ON TABLE sys_user IS '系统用户（员工账号）';
CREATE UNIQUE INDEX uk_user_empno    ON sys_user(employee_no) WHERE deleted = FALSE;
CREATE UNIQUE INDEX uk_user_username ON sys_user(username)   WHERE deleted = FALSE;
CREATE INDEX idx_user_dept           ON sys_user(dept_id)    WHERE deleted = FALSE;

-- 角色表
CREATE TABLE sys_role (
    id         BIGSERIAL PRIMARY KEY,
    code       VARCHAR(64) NOT NULL,  -- 如 ROLE_ADMIN / ROLE_KB_MANAGER / ROLE_EMPLOYEE
    name       VARCHAR(64) NOT NULL,
    remark     VARCHAR(255),
    status     SMALLINT    NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
COMMENT ON TABLE sys_role IS '角色表';
CREATE UNIQUE INDEX uk_role_code ON sys_role(code);

-- 权限表（菜单/按钮/接口权限点）
CREATE TABLE sys_permission (
    id         BIGSERIAL PRIMARY KEY,
    parent_id  BIGINT      NOT NULL DEFAULT 0,
    name       VARCHAR(64) NOT NULL,
    perms      VARCHAR(128),           -- 权限标识，如 kb:doc:upload
    type       SMALLINT    NOT NULL DEFAULT 1, -- 1 菜单 2 按钮 3 接口
    path       VARCHAR(255),
    sort       INT         NOT NULL DEFAULT 0,
    status     SMALLINT    NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
COMMENT ON TABLE sys_permission IS '权限表';
CREATE UNIQUE INDEX uk_permission_perms ON sys_permission(perms) WHERE perms IS NOT NULL;

-- 用户-角色关联
CREATE TABLE sys_user_role (
    user_id BIGINT NOT NULL REFERENCES sys_user(id),
    role_id BIGINT NOT NULL REFERENCES sys_role(id),
    PRIMARY KEY (user_id, role_id)
);

-- 角色-权限关联
CREATE TABLE sys_role_permission (
    role_id       BIGINT NOT NULL REFERENCES sys_role(id),
    permission_id BIGINT NOT NULL REFERENCES sys_permission(id),
    PRIMARY KEY (role_id, permission_id)
);

-- ============================ 二、知识库管理域 ============================

-- 知识库表
CREATE TABLE kb_knowledge_base (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(128) NOT NULL,
    description     TEXT,
    category        VARCHAR(64),                 -- 分类/标签
    visibility      VARCHAR(16)  NOT NULL DEFAULT 'DEPT', -- PRIVATE/DEPT/ORG/PUBLIC
    owner_id        BIGINT       NOT NULL REFERENCES sys_user(id), -- 创建人/负责人
    dept_id         BIGINT,                      -- visibility=DEPT 时可见部门
    chunk_size      INT          NOT NULL DEFAULT 512,   -- 默认分块大小(token)
    chunk_overlap   INT          NOT NULL DEFAULT 64,    -- 默认分块重叠(token)
    embedding_model VARCHAR(64)  NOT NULL DEFAULT 'bge-m3',
    status          SMALLINT     NOT NULL DEFAULT 1,     -- 1 启用 0 停用
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted         BOOLEAN      NOT NULL DEFAULT FALSE
);
COMMENT ON TABLE  kb_knowledge_base IS '知识库';

-- 知识库-允许访问部门关联。ORG/PUBLIC 知识库未配置记录时表示全组织可见；
-- DEPT 知识库至少应配置一个允许部门，用于支持跨部门协作授权。
CREATE TABLE kb_knowledge_base_dept (
    kb_id       BIGINT      NOT NULL REFERENCES kb_knowledge_base(id) ON DELETE CASCADE,
    dept_id     BIGINT      NOT NULL REFERENCES sys_dept(id),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (kb_id, dept_id)
);
COMMENT ON TABLE kb_knowledge_base_dept IS '知识库允许访问部门关联';
CREATE INDEX idx_kb_dept_dept ON kb_knowledge_base_dept(dept_id);
COMMENT ON COLUMN kb_knowledge_base.visibility IS '可见范围：PRIVATE仅自己 / DEPT本部门 / ORG全公司 / PUBLIC公开';
CREATE INDEX idx_kb_owner  ON kb_knowledge_base(owner_id) WHERE deleted = FALSE;
CREATE INDEX idx_kb_dept   ON kb_knowledge_base(dept_id)  WHERE deleted = FALSE;

-- 文档表（每个上传文件一条，含解析/索引状态与版本）
CREATE TABLE kb_document (
    id           BIGSERIAL PRIMARY KEY,
    kb_id        BIGINT       NOT NULL REFERENCES kb_knowledge_base(id),
    file_name    VARCHAR(255) NOT NULL,
    file_type    VARCHAR(32),                    -- pdf / docx / xlsx / pptx / md / txt
    file_size    BIGINT       NOT NULL DEFAULT 0,
    object_key   VARCHAR(512),                   -- MinIO/OSS 中的对象 key
    version      INT          NOT NULL DEFAULT 1,
    parse_status VARCHAR(16)  NOT NULL DEFAULT 'PENDING', -- PENDING/PARSING/SUCCESS/FAILED
    chunk_status VARCHAR(16)  NOT NULL DEFAULT 'PENDING', -- PENDING/INDEXING/INDEXED/PARTIAL/FAILED
    chunk_count  INT          NOT NULL DEFAULT 0,
    error_msg    TEXT,
    uploader_id  BIGINT       NOT NULL REFERENCES sys_user(id),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted      BOOLEAN      NOT NULL DEFAULT FALSE
);
COMMENT ON TABLE kb_document IS '知识库文档（版本管理：更新文档=新记录，旧记录保留历史）';
CREATE INDEX idx_doc_kb    ON kb_document(kb_id) WHERE deleted = FALSE;
CREATE INDEX idx_doc_parse ON kb_document(parse_status);
CREATE INDEX idx_doc_chunk ON kb_document(chunk_status);

-- 分块表（解析+切分后的文本片段）
CREATE TABLE kb_chunk (
    id            BIGSERIAL PRIMARY KEY,
    doc_id        BIGINT       NOT NULL REFERENCES kb_document(id),
    kb_id         BIGINT       NOT NULL,         -- 冗余，便于按库过滤
    seq           INT          NOT NULL,         -- 文档内块序号
    content       TEXT         NOT NULL,
    page_no       INT,
    token_count   INT,
    metadata      JSONB        NOT NULL DEFAULT '{}'::jsonb, -- 标题/章节/表格标识等
    embedding_id  VARCHAR(64),                   -- 对应 Qdrant point id
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uk_chunk_doc_seq UNIQUE (doc_id, seq)
);
COMMENT ON TABLE kb_chunk IS '文档分块';
CREATE INDEX idx_chunk_kb   ON kb_chunk(kb_id);
CREATE INDEX idx_chunk_doc  ON kb_chunk(doc_id);

-- ============================ 三、问答与审计域 ============================

-- 问答记录表
CREATE TABLE kb_qa_record (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT       NOT NULL REFERENCES sys_user(id),
    kb_ids              BIGINT[],                -- 本次检索的知识库范围（权限过滤后）
    question            TEXT         NOT NULL,
    answer              TEXT,
    retrieved_chunk_ids BIGINT[],                -- 召回并最终使用的 chunk
    rerank_scores       JSONB,                   -- 精排分数明细
    model_name          VARCHAR(64),
    prompt_version      VARCHAR(32),
    total_tokens        INT,
    latency_ms          INT,
    feedback            SMALLINT     NOT NULL DEFAULT 0, -- 1 赞 / -1 踩 / 0 无
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now()
);
COMMENT ON TABLE kb_qa_record IS 'RAG 问答记录（含检索与生成全链路留痕）';
CREATE INDEX idx_qa_user_time ON kb_qa_record(user_id, created_at DESC);

-- 反馈表
CREATE TABLE kb_feedback (
    id            BIGSERIAL PRIMARY KEY,
    qa_record_id  BIGINT      NOT NULL REFERENCES kb_qa_record(id),
    user_id       BIGINT      NOT NULL REFERENCES sys_user(id),
    rating        SMALLINT    NOT NULL,          -- 1 赞 / -1 踩
    comment       TEXT,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
COMMENT ON TABLE kb_feedback IS '问答反馈';
CREATE UNIQUE INDEX uk_feedback_qa ON kb_feedback(qa_record_id);

-- 审计日志表（大表建议按月分区，见文末扩展说明）
CREATE TABLE audit_log (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT,
    username    VARCHAR(64),
    action      VARCHAR(64) NOT NULL,            -- LOGIN / DOC_UPLOAD / DOC_DELETE / QA_ASK / KB_CREATE ...
    module      VARCHAR(32),
    object_type VARCHAR(32),
    object_id   BIGINT,
    detail      JSONB,                           -- 扩展信息（如问答摘要、删除的文档id）
    ip          VARCHAR(64),
    user_agent  VARCHAR(255),
    result      SMALLINT    NOT NULL DEFAULT 1,  -- 1 成功 0 失败
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
COMMENT ON TABLE audit_log IS '操作审计日志';
CREATE INDEX idx_audit_time   ON audit_log(created_at DESC);
CREATE INDEX idx_audit_user   ON audit_log(user_id);
CREATE INDEX idx_audit_action ON audit_log(action);

-- =====================================================================
-- 附：Qdrant 向量集合设计（业务上向量不落本库，建集合用如下配置）
-- =====================================================================
-- PUT /collections/kb_embeddings
-- {
--   "vectors": { "size": 1024, "distance": "Cosine" },
--   "payload_schema": {
--     "chunk_id":   { "data_type": "integer" },
--     "doc_id":     { "data_type": "integer" },
--     "kb_id":      { "data_type": "integer" },
--     "visibility": { "data_type": "keyword" },
--     "dept_id":    { "data_type": "integer" },
--     "seq":        { "data_type": "integer" },
--     "page_no":    { "data_type": "integer" },
--     "content":    { "data_type": "text" }
--   },
--   "indexes": [
--     { "field_name": "kb_id",      "field_schema": "integer" },
--     { "field_name": "dept_id",    "field_schema": "integer" },
--     { "field_name": "visibility", "field_schema": "keyword" }
--   ]
-- }
-- 检索时用 filter 实现权限：must 组合 visibility=ORG/PUBLIC 或 dept_id=当前部门 等，
-- 命中后把 point.payload.chunk_id 对应回 kb_chunk，用于引用溯源。
-- =====================================================================
-- 附：pgvector 起步方案（不使用 Qdrant 时，追加如下表）
-- =====================================================================
-- CREATE TABLE kb_embedding (
--     id        BIGSERIAL PRIMARY KEY,
--     chunk_id  BIGINT NOT NULL REFERENCES kb_chunk(id),
--     kb_id     BIGINT NOT NULL,
--     model     VARCHAR(64) NOT NULL DEFAULT 'bge-m3',
--     vector    vector(1024) NOT NULL,
--     created_at TIMESTAMPTZ NOT NULL DEFAULT now()
-- );
-- CREATE INDEX idx_emb_kb ON kb_embedding(kb_id);
-- CREATE INDEX idx_emb_vec ON kb_embedding USING hnsw (vector vector_cosine_ops);
-- =====================================================================
-- 附：审计日志按时间分区扩展（数据量大时启用）
-- =====================================================================
-- CREATE TABLE audit_log_2026_09 PARTITION OF audit_log
--     FOR VALUES FROM ('2026-09-01') TO ('2026-10-01');
-- （将 audit_log 改为 PARTITION BY RANGE (created_at)）
-- =====================================================================
