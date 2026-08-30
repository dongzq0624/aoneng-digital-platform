CREATE TABLE IF NOT EXISTS kb_conversation (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT       NOT NULL REFERENCES sys_user(id),
    title               VARCHAR(120) NOT NULL DEFAULT '新会话',
    selected_kb_ids     BIGINT[],
    last_message_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted_at          TIMESTAMPTZ,
    legacy_qa_record_id BIGINT       UNIQUE
);

CREATE INDEX IF NOT EXISTS idx_conversation_user_updated
    ON kb_conversation(user_id, last_message_at DESC, id DESC)
    WHERE deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS kb_chat_message (
    id                  BIGSERIAL PRIMARY KEY,
    conversation_id     BIGINT       NOT NULL REFERENCES kb_conversation(id) ON DELETE CASCADE,
    sequence_no         INT          NOT NULL,
    role                VARCHAR(16)  NOT NULL,
    content             TEXT         NOT NULL DEFAULT '',
    status              VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    source_kb_ids       BIGINT[],
    retrieved_chunk_ids BIGINT[],
    model_name          VARCHAR(64),
    latency_ms          INT,
    error_message       VARCHAR(500),
    qa_record_id        BIGINT       UNIQUE REFERENCES kb_qa_record(id) ON DELETE SET NULL,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    completed_at        TIMESTAMPTZ,
    CONSTRAINT uk_chat_message_sequence UNIQUE (conversation_id, sequence_no),
    CONSTRAINT ck_chat_message_role CHECK (role IN ('USER', 'ASSISTANT', 'SYSTEM')),
    CONSTRAINT ck_chat_message_status CHECK (status IN ('PENDING', 'STREAMING', 'COMPLETED', 'FAILED', 'CANCELLED'))
);

CREATE INDEX IF NOT EXISTS idx_chat_message_conversation
    ON kb_chat_message(conversation_id, sequence_no DESC);

CREATE TABLE IF NOT EXISTS kb_chat_message_citation (
    id          BIGSERIAL PRIMARY KEY,
    message_id  BIGINT       NOT NULL REFERENCES kb_chat_message(id) ON DELETE CASCADE,
    chunk_id    BIGINT,
    doc_id      BIGINT,
    kb_id       BIGINT,
    file_name   VARCHAR(255) NOT NULL,
    page_no     INT,
    snippet     TEXT         NOT NULL,
    rank_no     INT          NOT NULL,
    score       DOUBLE PRECISION,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uk_chat_message_citation_rank UNIQUE (message_id, rank_no)
);

CREATE INDEX IF NOT EXISTS idx_chat_citation_message ON kb_chat_message_citation(message_id, rank_no);

ALTER TABLE kb_qa_record ADD COLUMN IF NOT EXISTS conversation_id BIGINT;
ALTER TABLE kb_qa_record ADD COLUMN IF NOT EXISTS assistant_message_id BIGINT;
CREATE INDEX IF NOT EXISTS idx_qa_conversation_time
    ON kb_qa_record(conversation_id, created_at DESC)
    WHERE conversation_id IS NOT NULL;

INSERT INTO kb_conversation (user_id, title, selected_kb_ids, last_message_at, created_at, updated_at, legacy_qa_record_id)
SELECT q.user_id,
       LEFT(COALESCE(NULLIF(TRIM(q.question), ''), '历史问答'), 120),
       q.kb_ids,
       q.created_at,
       q.created_at,
       q.created_at,
       q.id
FROM kb_qa_record q
WHERE q.conversation_id IS NULL
  AND NOT EXISTS (
    SELECT 1 FROM kb_conversation c WHERE c.legacy_qa_record_id = q.id
);

UPDATE kb_qa_record q
SET conversation_id = c.id
FROM kb_conversation c
WHERE c.legacy_qa_record_id = q.id
  AND q.conversation_id IS NULL;

INSERT INTO kb_chat_message (conversation_id, sequence_no, role, content, status, source_kb_ids, created_at, completed_at)
SELECT c.id, 1, 'USER', q.question, 'COMPLETED', q.kb_ids, q.created_at, q.created_at
FROM kb_qa_record q
JOIN kb_conversation c ON c.legacy_qa_record_id = q.id
WHERE NOT EXISTS (
    SELECT 1 FROM kb_chat_message m WHERE m.conversation_id = c.id AND m.sequence_no = 1
);

INSERT INTO kb_chat_message (conversation_id, sequence_no, role, content, status, source_kb_ids, retrieved_chunk_ids, model_name, latency_ms, qa_record_id, created_at, completed_at)
SELECT c.id,
       2,
       'ASSISTANT',
       COALESCE(q.answer, ''),
       'COMPLETED',
       q.kb_ids,
       q.retrieved_chunk_ids,
       q.model_name,
       q.latency_ms,
       q.id,
       q.created_at,
       q.created_at
FROM kb_qa_record q
JOIN kb_conversation c ON c.legacy_qa_record_id = q.id
ON CONFLICT (qa_record_id) DO NOTHING;

UPDATE kb_qa_record q
SET assistant_message_id = m.id
FROM kb_chat_message m
WHERE m.qa_record_id = q.id
  AND q.assistant_message_id IS NULL;
