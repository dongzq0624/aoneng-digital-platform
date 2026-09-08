# RAGAS evaluator

The service is internal to the Compose network. `POST /v1/evaluate` accepts a QA record
and retrieved chunk IDs, resolves chunk text from PostgreSQL, and matches the question to
an enabled `kb_retrieval_eval_case`. Reference-based metrics use the labelled expected
chunk IDs when available; a missing reference is reported explicitly instead of using the
generated answer as ground truth.

Create labelled data before expecting `context_recall`:

```sql
INSERT INTO kb_retrieval_eval_case(question, expected_chunk_ids, reference_answer)
VALUES ('问题', ARRAY[123, 456], '人工确认的标准答案');
```
