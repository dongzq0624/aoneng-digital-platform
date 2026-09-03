CREATE TABLE IF NOT EXISTS rag_observation_event (
    id BIGSERIAL PRIMARY KEY,
    trace_id VARCHAR(64) NOT NULL,
    operation VARCHAR(96) NOT NULL,
    doc_id BIGINT,
    kb_id BIGINT,
    conversation_id BIGINT,
    duration_ms NUMERIC(14,3) NOT NULL DEFAULT 0,
    success BOOLEAN NOT NULL,
    attributes JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_rag_observation_time ON rag_observation_event(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_rag_observation_trace ON rag_observation_event(trace_id);
CREATE INDEX IF NOT EXISTS idx_rag_observation_scope ON rag_observation_event(kb_id, doc_id, conversation_id);
