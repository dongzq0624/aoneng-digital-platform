ALTER TABLE kb_retrieval_eval_case
    ADD COLUMN IF NOT EXISTS reference_answer TEXT;

COMMENT ON COLUMN kb_retrieval_eval_case.reference_answer IS
    '人工确认的参考答案，用于 RAGAS context recall 与 context precision';
