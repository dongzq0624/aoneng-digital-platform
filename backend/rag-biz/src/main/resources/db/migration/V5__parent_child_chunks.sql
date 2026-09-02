CREATE TABLE IF NOT EXISTS kb_parent_chunk (
    id          BIGSERIAL PRIMARY KEY,
    doc_id      BIGINT NOT NULL REFERENCES kb_document(id) ON DELETE CASCADE,
    kb_id       BIGINT NOT NULL REFERENCES kb_knowledge_base(id) ON DELETE CASCADE,
    seq         INT NOT NULL,
    content     TEXT NOT NULL,
    page_no     INT,
    token_count INT NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER TABLE kb_chunk
    ADD COLUMN IF NOT EXISTS parent_id BIGINT REFERENCES kb_parent_chunk(id) ON DELETE CASCADE;

CREATE INDEX IF NOT EXISTS idx_kb_parent_chunk_doc ON kb_parent_chunk(doc_id, seq);
CREATE INDEX IF NOT EXISTS idx_kb_chunk_parent ON kb_chunk(parent_id);
