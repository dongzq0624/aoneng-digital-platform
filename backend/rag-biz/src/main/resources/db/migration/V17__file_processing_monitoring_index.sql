CREATE INDEX IF NOT EXISTS idx_kb_document_processing_time
    ON kb_document(created_at DESC)
    WHERE deleted = FALSE;

CREATE INDEX IF NOT EXISTS idx_rag_observation_doc_time
    ON rag_observation_event(doc_id, created_at DESC)
    WHERE doc_id IS NOT NULL;
