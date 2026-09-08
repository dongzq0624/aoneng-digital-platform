import math
import os
import re
from typing import Any

import psycopg
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field


DATABASE_URL = os.getenv("DATABASE_URL", "postgresql://rag:rag123456@postgres:5432/rag_platform")
RAGAS_LLM_MODEL = os.getenv("RAGAS_LLM_MODEL", "qwen-plus")
RAGAS_EMBEDDING_MODEL = os.getenv("RAGAS_EMBEDDING_MODEL", "text-embedding-v4")
RAGAS_API_KEY = os.getenv("RAGAS_API_KEY", "") or os.getenv("DASHSCOPE_API_KEY", "")
RAGAS_BASE_URL = os.getenv(
    "RAGAS_BASE_URL", "https://dashscope.aliyuncs.com/compatible-mode/v1"
)

app = FastAPI(title="Aoneng RAGAS Evaluator", version="1.0")


class EvaluateRequest(BaseModel):
    question: str = Field(min_length=1, max_length=20000)
    answer: str = Field(min_length=1, max_length=100000)
    retrievedChunkIds: list[int] = Field(default_factory=list, max_length=100)
    qaRecordId: int = Field(gt=0)
    referenceAnswer: str | None = Field(default=None, max_length=20000)


def db_fetch(request: EvaluateRequest) -> tuple[list[str], str | None, list[int], list[int]]:
    """Resolve the exact contexts and human-labelled reference data in PostgreSQL."""
    ids = list(dict.fromkeys(int(value) for value in request.retrievedChunkIds if int(value) > 0))
    with psycopg.connect(DATABASE_URL, connect_timeout=3) as connection:
        with connection.cursor() as cursor:
            cursor.execute(
                "SELECT question, answer FROM kb_qa_record WHERE id = %s",
                (request.qaRecordId,),
            )
            record = cursor.fetchone()
            if record is None:
                raise HTTPException(status_code=404, detail="QA record not found")

            # Preserve retrieval order because context precision is rank-sensitive.
            contexts_by_id: dict[int, str] = {}
            if ids:
                cursor.execute(
                    "SELECT id, content FROM kb_chunk WHERE id = ANY(%s::bigint[])",
                    (ids,),
                )
                contexts_by_id = {
                    int(chunk_id): str(content or "").strip()
                    for chunk_id, content in cursor.fetchall()
                }
            contexts = [contexts_by_id[chunk_id] for chunk_id in ids if contexts_by_id.get(chunk_id)]

            cursor.execute(
                """
                SELECT reference_answer, expected_chunk_ids
                  FROM kb_retrieval_eval_case
                 WHERE enabled = TRUE
                   AND lower(trim(question)) = lower(trim(%s))
                 ORDER BY id
                 LIMIT 1
                """,
                (str(record[0] or request.question),),
            )
            labelled = cursor.fetchone()

    reference = request.referenceAnswer.strip() if request.referenceAnswer else None
    expected: list[int] = []
    if labelled:
        reference = reference or (str(labelled[0]).strip() if labelled[0] else None)
        expected = [int(value) for value in (labelled[1] or []) if int(value) > 0]
    return contexts, reference, expected, ids


TOKEN_RE = re.compile(r"[\u4e00-\u9fff]|[a-zA-Z0-9]+")


def tokens(value: str) -> set[str]:
    return {token.lower() for token in TOKEN_RE.findall(value or "")}


def ragas_score(
    request: EvaluateRequest,
    contexts: list[str],
    reference: str | None,
    expected_ids: list[int],
    retrieved_ids: list[int],
) -> dict[str, Any]:
    if not contexts:
        raise HTTPException(status_code=422, detail="retrieved chunk contents are empty")

    # Keep the service deterministic and lightweight. The labels are the same four
    # RAGAS dimensions, while reference-context recall is exact when expected chunk IDs
    # are supplied by an evaluation case. This avoids silently scoring against the model's
    # own answer and keeps the service deployable without a second model runtime.
    context_tokens = tokens("\n".join(contexts))
    question_tokens = tokens(request.question)
    answer_tokens = tokens(request.answer)
    reference_tokens = tokens(reference or "")

    def ratio(numerator: int, denominator: int) -> float:
        return 0.0 if denominator <= 0 else round(max(0.0, min(1.0, numerator / denominator)), 6)

    answer_relevancy = ratio(len(question_tokens & answer_tokens), len(question_tokens))
    faithfulness = ratio(len(answer_tokens & context_tokens), len(answer_tokens))
    if reference_tokens:
        context_recall = ratio(len(reference_tokens & context_tokens), len(reference_tokens))
        # Expected IDs provide an explicit relevance label for rank precision when
        # available; otherwise use answer-token coverage as a conservative fallback.
        if expected_ids:
            expected = set(expected_ids)
            retrieved = set(retrieved_ids)
            context_precision = ratio(len(expected & retrieved), len(retrieved))
            precision_source = "expected_chunk_ids"
        else:
            context_precision = ratio(len(reference_tokens & context_tokens), len(context_tokens))
            precision_source = "reference_answer_tokens"
        recall_source = "reference_answer_tokens"
    elif expected_ids:
        expected = set(expected_ids)
        retrieved = set(retrieved_ids)
        context_recall = ratio(len(expected & retrieved), len(expected))
        context_precision = ratio(len(expected & retrieved), len(retrieved))
        recall_source = "expected_chunk_ids"
        precision_source = "expected_chunk_ids"
    else:
        context_recall = None
        context_precision = None
        recall_source = "missing_reference"
        precision_source = "missing_reference"

    return {
        "faithfulness": faithfulness,
        "answer_relevancy": answer_relevancy,
        "context_precision": context_precision,
        "context_recall": context_recall,
        "referenceAnswerAvailable": bool(reference),
        "contextRecallSource": recall_source,
        "contextPrecisionSource": precision_source,
        "contextCount": len(contexts),
    }


@app.get("/health")
def health() -> dict[str, Any]:
    return {
        "status": "UP",
        "service": "ragas",
        "model": RAGAS_LLM_MODEL,
        "embeddingModel": RAGAS_EMBEDDING_MODEL,
        "databaseConfigured": bool(DATABASE_URL),
        "apiKeyConfigured": bool(RAGAS_API_KEY),
    }


@app.post("/v1/evaluate")
def evaluate(request: EvaluateRequest) -> dict[str, Any]:
    contexts, reference, expected, retrieved = db_fetch(request)
    scores = ragas_score(request, contexts, reference, expected, retrieved)
    scores["qaRecordId"] = request.qaRecordId
    scores["retrievedChunkIds"] = request.retrievedChunkIds
    scores["expectedChunkIds"] = expected
    return scores
