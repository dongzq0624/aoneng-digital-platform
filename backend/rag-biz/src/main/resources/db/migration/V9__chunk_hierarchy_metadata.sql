ALTER TABLE kb_parent_chunk
    ADD COLUMN IF NOT EXISTS metadata JSONB NOT NULL DEFAULT '{}'::jsonb;

ALTER TABLE kb_chunk
    ADD COLUMN IF NOT EXISTS chunk_level VARCHAR(16) NOT NULL DEFAULT 'CHILD';

CREATE INDEX IF NOT EXISTS idx_kb_chunk_level ON kb_chunk(doc_id, chunk_level);
