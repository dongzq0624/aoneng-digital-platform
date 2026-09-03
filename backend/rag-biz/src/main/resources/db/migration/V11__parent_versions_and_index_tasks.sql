ALTER TABLE kb_parent_chunk
    ADD COLUMN IF NOT EXISTS document_version INT NOT NULL DEFAULT 1;

CREATE INDEX IF NOT EXISTS idx_parent_doc_version
    ON kb_parent_chunk(doc_id, document_version, seq);

CREATE TABLE IF NOT EXISTS kb_index_task (
    id BIGSERIAL PRIMARY KEY,
    doc_id BIGINT NOT NULL REFERENCES kb_document(id) ON DELETE CASCADE,
    document_version INT NOT NULL,
    operation VARCHAR(16) NOT NULL DEFAULT 'UPSERT',
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    attempts INT NOT NULL DEFAULT 0,
    next_retry_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_error TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_kb_index_task_version UNIQUE (doc_id, document_version, operation)
);
CREATE INDEX IF NOT EXISTS idx_kb_index_task_poll
    ON kb_index_task(status, next_retry_at);

CREATE OR REPLACE VIEW kb_chunk_quality_metrics AS
SELECT d.id AS doc_id,
       d.file_name,
       COUNT(DISTINCT p.id) AS parent_count,
       COUNT(c.id) AS child_count,
       COALESCE(AVG(p.token_count), 0) AS avg_parent_tokens,
       COALESCE(AVG(c.token_count), 0) AS avg_child_tokens,
       COUNT(*) FILTER (WHERE c.content = p.content) AS identical_child_count,
       COUNT(*) FILTER (WHERE p.embedding_id IS NULL OR p.embedding_id = '') AS parent_without_embedding_count,
       COUNT(DISTINCT p.id) FILTER (WHERE (p.metadata->>'start_page_no')::int IS NOT NULL
                                           AND (p.metadata->>'end_page_no')::int IS NOT NULL
                                           AND (p.metadata->>'start_page_no')::int < (p.metadata->>'end_page_no')::int)
           AS cross_page_parent_count,
       CASE WHEN COUNT(DISTINCT p.id) = 0 THEN 0
            ELSE ROUND(100.0 * COUNT(DISTINCT p.id) FILTER (WHERE (p.metadata->>'start_page_no')::int IS NOT NULL
                                           AND (p.metadata->>'end_page_no')::int IS NOT NULL
                                           AND (p.metadata->>'start_page_no')::int < (p.metadata->>'end_page_no')::int)
                      / COUNT(DISTINCT p.id), 2) END AS cross_page_parent_ratio
  FROM kb_document d
  LEFT JOIN kb_parent_chunk p ON p.doc_id = d.id
  LEFT JOIN kb_chunk c ON c.parent_id = p.id
 GROUP BY d.id, d.file_name;
