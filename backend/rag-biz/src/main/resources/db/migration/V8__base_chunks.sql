CREATE TABLE IF NOT EXISTS kb_base_chunk (
    id              BIGSERIAL PRIMARY KEY,
    doc_id          BIGINT NOT NULL REFERENCES kb_document(id) ON DELETE CASCADE,
    kb_id           BIGINT NOT NULL REFERENCES kb_knowledge_base(id) ON DELETE CASCADE,
    seq             INT NOT NULL,
    content         TEXT NOT NULL,
    page_no         INT,
    token_count     INT NOT NULL DEFAULT 0,
    block_type      VARCHAR(32),
    layout_metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_base_chunk_doc_seq UNIQUE (doc_id, seq)
);
CREATE INDEX IF NOT EXISTS idx_base_chunk_doc ON kb_base_chunk(doc_id, seq);
CREATE INDEX IF NOT EXISTS idx_base_chunk_kb ON kb_base_chunk(kb_id);

ALTER TABLE kb_parent_chunk ADD COLUMN IF NOT EXISTS embedding_id VARCHAR(64);
CREATE INDEX IF NOT EXISTS idx_parent_embedding ON kb_parent_chunk(embedding_id);
