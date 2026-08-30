CREATE TABLE IF NOT EXISTS kb_retrieval_eval_case (
    id                BIGSERIAL PRIMARY KEY,
    question          TEXT NOT NULL,
    expected_chunk_ids BIGINT[] NOT NULL DEFAULT '{}',
    enabled           BOOLEAN NOT NULL DEFAULT TRUE,
    note              VARCHAR(500),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_retrieval_eval_enabled
    ON kb_retrieval_eval_case(enabled, id);
