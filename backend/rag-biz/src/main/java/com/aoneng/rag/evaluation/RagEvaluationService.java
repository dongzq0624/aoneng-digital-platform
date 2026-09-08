package com.aoneng.rag.evaluation;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.util.List;
import java.util.Map;

/** Asynchronous RAGAS evaluation queue with optional GEPA recommendations. */
@Service
public class RagEvaluationService {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(RagEvaluationService.class);
    private final JdbcTemplate jdbc;
    private final RestClient ragas;
    private final RestClient gepa;
    private final ObjectMapper mapper = new ObjectMapper();
    private final boolean enabled;
    private final boolean optimizerEnabled;
    private final boolean backfillOnStart;

    public RagEvaluationService(JdbcTemplate jdbc,
                                @Value("${rag.evaluation.enabled:false}") boolean enabled,
                                @Value("${rag.evaluation.endpoint:http://ragas:8095}") String endpoint,
                                @Value("${rag.evaluation.optimizer-enabled:false}") boolean optimizerEnabled,
                                @Value("${rag.evaluation.optimizer-endpoint:http://gepa:8096}") String optimizerEndpoint,
                                @Value("${rag.evaluation.backfill-on-start:true}") boolean backfillOnStart) {
        this.jdbc = jdbc;
        this.enabled = enabled;
        this.optimizerEnabled = optimizerEnabled;
        this.backfillOnStart = backfillOnStart;
        this.ragas = client(endpoint);
        this.gepa = client(optimizerEndpoint);
    }

    private RestClient client(String endpoint) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3_000);
        factory.setReadTimeout(15_000);
        return RestClient.builder().baseUrl(endpoint).requestFactory(factory).build();
    }

    public void enqueue(long qaRecordId) {
        if (!enabled || qaRecordId <= 0) return;
        try {
            jdbc.update("INSERT INTO rag_evaluation_task(qa_record_id) VALUES(?) ON CONFLICT (qa_record_id) DO NOTHING", qaRecordId);
        } catch (Exception failure) {
            log.warn("创建 RAGAS 评估任务失败: qaRecordId={}", qaRecordId, failure);
        }
    }

    /** Backfill recent completed answers so enabling evaluation also covers existing QA history. */
    @PostConstruct
    public void backfillRecentRecords() {
        if (!enabled || !backfillOnStart) return;
        try {
            jdbc.update("""
                    INSERT INTO rag_evaluation_task(qa_record_id)
                    SELECT q.id
                      FROM kb_qa_record q
                     WHERE q.created_at >= now() - interval '30 days'
                       AND q.answer IS NOT NULL AND btrim(q.answer) <> ''
                       AND cardinality(COALESCE(q.retrieved_chunk_ids, ARRAY[]::bigint[])) > 0
                    ON CONFLICT (qa_record_id) DO NOTHING
                    """);
            jdbc.update("""
                    UPDATE rag_evaluation_task t
                       SET status='FAILED',
                           last_error='检索分块不存在，无法读取评估上下文',
                           updated_at=now()
                     WHERE t.status='PENDING'
                       AND NOT EXISTS (
                           SELECT 1
                             FROM kb_qa_record q
                             JOIN kb_chunk c ON c.id = ANY(q.retrieved_chunk_ids)
                            WHERE q.id=t.qa_record_id
                       )
                    """);
        } catch (Exception failure) {
            log.warn("回补 RAGAS 评估任务失败", failure);
        }
    }

    @Scheduled(fixedDelayString = "${rag.evaluation.poll-ms:60000}", initialDelayString = "${rag.evaluation.poll-ms:60000}")
    public void processDueTasks() {
        if (!enabled) return;
        List<Map<String, Object>> tasks = jdbc.queryForList("""
                SELECT t.id, t.qa_record_id, q.question, q.answer, q.retrieved_chunk_ids
                  FROM rag_evaluation_task t JOIN kb_qa_record q ON q.id=t.qa_record_id
                 WHERE t.status='PENDING' AND t.next_retry_at <= now()
                 ORDER BY t.next_retry_at, t.id LIMIT 4
                """);
        for (Map<String, Object> task : tasks) evaluate(task);
    }

    private void evaluate(Map<String, Object> task) {
        long taskId = number(task.get("id"));
        long qaId = number(task.get("qa_record_id"));
        if (jdbc.update("UPDATE rag_evaluation_task SET status='RUNNING', attempts=attempts+1, updated_at=now() WHERE id=? AND status='PENDING'", taskId) == 0) return;
        try {
            Map<String, Object> payload = Map.of("question", safe(task.get("question")), "answer", safe(task.get("answer")),
                    "retrievedChunkIds", chunkIds(task.get("retrieved_chunk_ids")),
                    "qaRecordId", qaId);
            Map<?, ?> result = ragas.post().uri("/v1/evaluate").contentType(MediaType.APPLICATION_JSON)
                    .body(payload).retrieve().body(Map.class);
            String metrics = mapper.writeValueAsString(result == null ? Map.of() : result);
            jdbc.update("UPDATE rag_evaluation_task SET status='SUCCESS', metrics=?::jsonb, last_error=NULL, updated_at=now() WHERE id=?", metrics, taskId);
            if (optimizerEnabled && result != null) optimize(taskId, result);
        } catch (Exception failure) {
            String error = failure.getClass().getSimpleName() + ": " + String.valueOf(failure.getMessage());
            jdbc.update("UPDATE rag_evaluation_task SET status=CASE WHEN attempts>=5 THEN 'FAILED' ELSE 'PENDING' END, last_error=?, next_retry_at=now() + (LEAST(attempts,5)*2 || ' minutes')::interval, updated_at=now() WHERE id=?", error.substring(0, Math.min(500, error.length())), taskId);
            log.warn("RAGAS 评估失败: taskId={}, qaRecordId={}", taskId, qaId, failure);
        }
    }

    private void optimize(long taskId, Map<?, ?> metrics) {
        try {
            Map<?, ?> recommendation = gepa.post().uri("/v1/optimize").contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("metrics", metrics)).retrieve().body(Map.class);
            if (recommendation != null) jdbc.update("INSERT INTO rag_parameter_recommendation(evaluation_task_id, parameters) VALUES(?,?::jsonb)", taskId, mapper.writeValueAsString(recommendation));
        } catch (Exception failure) {
            log.warn("GEPA 参数优化失败: evaluationTaskId={}", taskId, failure);
        }
    }

    private static long number(Object value) { try { return value == null ? 0 : Long.parseLong(String.valueOf(value)); } catch (Exception ignored) { return 0; } }
    private static String safe(Object value) { return value == null ? "" : String.valueOf(value); }

    private static List<Long> chunkIds(Object value) {
        if (value == null) return List.of();
        try {
            if (value instanceof java.sql.Array array) value = array.getArray();
        } catch (Exception ignored) {
            return List.of();
        }
        if (value instanceof long[] values) {
            List<Long> result = new java.util.ArrayList<>(values.length);
            for (long item : values) if (item > 0) result.add(item);
            return result;
        }
        if (value instanceof Object[] values) {
            List<Long> result = new java.util.ArrayList<>(values.length);
            for (Object item : values) { long id = number(item); if (id > 0) result.add(id); }
            return result;
        }
        String raw = String.valueOf(value).trim();
        if (raw.startsWith("{") && raw.endsWith("}")) raw = raw.substring(1, raw.length() - 1);
        if (raw.isBlank()) return List.of();
        List<Long> result = new java.util.ArrayList<>();
        for (String item : raw.split(",")) { long id = number(item.trim()); if (id > 0) result.add(id); }
        return result;
    }
}
