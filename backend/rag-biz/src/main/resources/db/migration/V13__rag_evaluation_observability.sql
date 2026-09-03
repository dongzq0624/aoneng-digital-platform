CREATE TABLE IF NOT EXISTS rag_evaluation_task (
    id BIGSERIAL PRIMARY KEY,
    qa_record_id BIGINT NOT NULL REFERENCES kb_qa_record(id) ON DELETE CASCADE,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    attempts INT NOT NULL DEFAULT 0,
    next_retry_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    metrics JSONB,
    last_error TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_rag_eval_qa UNIQUE (qa_record_id)
);
CREATE INDEX IF NOT EXISTS idx_rag_eval_poll ON rag_evaluation_task(status, next_retry_at);

CREATE TABLE IF NOT EXISTS rag_parameter_recommendation (
    id BIGSERIAL PRIMARY KEY,
    evaluation_task_id BIGINT REFERENCES rag_evaluation_task(id) ON DELETE SET NULL,
    parameters JSONB NOT NULL,
    applied BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

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
