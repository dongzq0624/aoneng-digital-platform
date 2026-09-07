package com.aoneng.rag.monitoring;

import com.aoneng.rag.application.repository.PlatformRepository;
import com.aoneng.rag.application.repository.KbScope;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class MonitoringService {
    private static final ZoneId DISPLAY_ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter DISPLAY_DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final JdbcTemplate jdbc;
    private final PlatformRepository repo;

    public MonitoringService(JdbcTemplate jdbc, PlatformRepository repo) {
        this.jdbc = jdbc;
        this.repo = repo;
    }

    public void requireAdmin(String username) {
        KbScope scope = repo.kbScope(username);
        if (!scope.admin()) throw new SecurityException("仅系统管理员可以查看 RAG 运行监控");
    }

    public Map<String, Object> overview(String from, String to, Long kbId, Long docId, Long conversationId) {
        Filter filter = filter(from, to, kbId, docId, conversationId);
        List<Map<String, Object>> stages = jdbc.queryForList(
                "SELECT operation, COUNT(*) AS calls, ROUND(AVG(duration_ms),3) AS avg_ms, " +
                        "ROUND(PERCENTILE_CONT(0.50) WITHIN GROUP (ORDER BY duration_ms)::numeric,3) AS p50_ms, " +
                        "ROUND(PERCENTILE_CONT(0.95) WITHIN GROUP (ORDER BY duration_ms)::numeric,3) AS p95_ms, " +
                        "ROUND(100.0 * AVG(CASE WHEN success THEN 1 ELSE 0 END),2) AS success_rate " +
                        "FROM rag_observation_event " + filter.sql + " GROUP BY operation ORDER BY operation", filter.args.toArray());
        Map<String, Object> quality = jdbc.queryForMap(
                "SELECT COUNT(*) FILTER (WHERE t.status='SUCCESS') AS evaluated, COUNT(*) FILTER (WHERE t.status='FAILED') AS failed, " +
                        "COALESCE(AVG(" + metric("faithfulness") + "),0) AS faithfulness, " +
                        "COALESCE(AVG(" + metric("answer_correctness") + "),0) AS answer_correctness, " +
                        "COALESCE(AVG(" + metric("context_precision") + "),0) AS context_precision, " +
                        "COALESCE(AVG(" + metric("context_recall") + "),0) AS context_recall, " +
                        "COALESCE(AVG(" + metric("citation_completeness") + "),0) AS citation_completeness " +
                        "FROM rag_evaluation_task t JOIN kb_qa_record q ON q.id=t.qa_record_id " +
                        qualityWhere(filter, kbId, docId, conversationId), qualityArgs(filter, kbId, docId, conversationId).toArray());
        Number recall = jdbc.queryForObject(
                "SELECT COALESCE(AVG(" + recallMetricExpression() + "),0) " +
                        "FROM rag_evaluation_task t JOIN kb_qa_record q ON q.id=t.qa_record_id " +
                        qualityWhere(filter, kbId, docId, conversationId) + " AND t.status='SUCCESS'",
                Number.class, qualityArgs(filter, kbId, docId, conversationId).toArray());
        quality.put("recallAt5", toRatio(recall));
        Map<String, Object> summary = jdbc.queryForMap(
                "SELECT COUNT(*) AS calls, COALESCE(SUM(duration_ms),0) AS total_ms, " +
                        "COALESCE(SUM(CASE WHEN NOT success THEN 1 ELSE 0 END),0) AS errors " +
                        "FROM rag_observation_event " + filter.sql, filter.args.toArray());
        Map<String, Object> summaryWithRate = new LinkedHashMap<>(summary);
        double seconds = Math.max(1D, java.time.Duration.between(filter.from, filter.to).toMillis() / 1000D);
        Number calls = (Number) summary.getOrDefault("calls", 0);
        summaryWithRate.put("throughput", calls.doubleValue() / seconds);
        Map<String, Object> performance = performance(stages, filter);
        return Map.of("from", filter.from, "to", filter.to, "stages", stages, "quality", quality, "summary", summaryWithRate, "performance", performance);
    }

    /** 按文件聚合文档处理流水线各阶段耗时，保留没有观测事件的文档以便定位任务卡住问题。 */
    public Map<String, Object> fileProcessing(String from, String to, int page, int pageSize) {
        boolean unbounded = !hasText(from) && !hasText(to);
        Filter filter = unbounded ? null : filter(from, to, null, null, null);
        String documentSql = "SELECT d.id AS \"docId\", d.file_name AS \"fileName\", d.file_type AS \"fileType\", " +
                "d.parse_status AS \"parseStatus\", d.chunk_status AS \"chunkStatus\", " +
                "LEFT(COALESCE(d.error_msg,''),500) AS \"errorMessage\", d.created_at AS \"createdAt\" " +
                "FROM kb_document d WHERE d.deleted=FALSE";
        List<Object> documentArgs = new ArrayList<>();
        if (unbounded) {
            documentSql += " ORDER BY d.created_at DESC";
        } else {
            documentSql += " AND (d.created_at BETWEEN ? AND ? OR EXISTS " +
                    "(SELECT 1 FROM rag_observation_event e WHERE e.doc_id=d.id AND e.created_at BETWEEN ? AND ?)) " +
                    "ORDER BY d.created_at DESC";
            documentArgs.add(filter.from);
            documentArgs.add(filter.to);
            documentArgs.add(filter.from);
            documentArgs.add(filter.to);
        }
        List<Map<String, Object>> documents = jdbc.queryForList(documentSql, documentArgs.toArray());
        List<Map<String, Object>> items = new ArrayList<>();
        for (Map<String, Object> document : documents) {
            long id = ((Number) document.get("docId")).longValue();
            String eventSql = "SELECT operation, COALESCE(SUM(duration_ms),0) AS duration_ms, " +
                    "BOOL_AND(success) AS success, MAX(attributes->>'parse_method') AS parse_method, " +
                    "MAX(attributes->>'error_type') AS error_type, " +
                    "MAX(attributes->>'timing_version') AS timing_version " +
                    "FROM rag_observation_event WHERE doc_id=?";
            List<Object> eventArgs = new ArrayList<>(List.of(id));
            if (!unbounded) {
                eventSql += " AND created_at BETWEEN ? AND ?";
                eventArgs.add(filter.from);
                eventArgs.add(filter.to);
            }
            eventSql += " GROUP BY operation";
            List<Map<String, Object>> events = jdbc.queryForList(eventSql, eventArgs.toArray());
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("docId", id);
            item.put("fileName", document.get("fileName"));
            item.put("fileType", document.get("fileType"));
            item.put("parseMethod", "-");
            Map<String, Double> durations = new LinkedHashMap<>();
            String errorType = null;
            boolean independentTiming = false;
            for (Map<String, Object> event : events) {
                String operation = String.valueOf(event.get("operation"));
                Number duration = (Number) event.get("duration_ms");
                durations.put(operation, duration == null ? 0D : duration.doubleValue());
                if ("document.parse".equals(operation) && event.get("parse_method") != null) {
                    item.put("parseMethod", event.get("parse_method"));
                }
                if ("2".equals(String.valueOf(event.get("timing_version")))) {
                    independentTiming = true;
                }
                if (Boolean.FALSE.equals(event.get("success")) && event.get("error_type") != null) {
                    errorType = String.valueOf(event.get("error_type"));
                }
            }
            double upload = value(durations, "object.upload");
            double parse = value(durations, "document.parse");
            double layout = value(durations, "document.base_chunk");
            double parentChild = value(durations, "document.parent_child_chunk", "document.chunk");
            double vector = value(durations, "embedding.dense") + value(durations, "embedding.sparse_bm25");
            double postgres = value(durations, "document.persist_database");
            double milvus = value(durations, "vector.upsert");
            // Version 2 records three independent processing stages. Legacy events
            // used document.parse as a superset of base_chunk, so retain the
            // historical de-duplication rule when no v2 event is present.
            double parseAndLayout = independentTiming ? parse + layout : Math.max(parse, layout);
            double total = upload + parseAndLayout + parentChild + vector + postgres + milvus;
            item.put("uploadMs", upload);
            item.put("parseMs", parse);
            item.put("layoutChunkMs", layout);
            item.put("parentChildChunkMs", parentChild);
            item.put("vectorizationMs", vector);
            item.put("postgresMs", postgres);
            item.put("milvusMs", milvus);
            item.put("totalMs", total);
            String parseStatus = String.valueOf(document.get("parseStatus"));
            String chunkStatus = String.valueOf(document.get("chunkStatus"));
            item.put("status", "FAILED".equals(parseStatus) || "FAILED".equals(chunkStatus)
                    ? "FAILED" : "INDEXED".equals(chunkStatus) ? "INDEXED" : "PROCESSING");
            String message = String.valueOf(document.getOrDefault("errorMessage", ""));
            item.put("errorMessage", message.isBlank() && errorType != null ? errorType : (message.isBlank() ? null : message));
            items.add(item);
        }
        int safePage = Math.max(1, page);
        int safeSize = Math.max(1, Math.min(pageSize, 100));
        int total = items.size();
        int fromIndex = Math.min((safePage - 1) * safeSize, total);
        int toIndex = Math.min(fromIndex + safeSize, total);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("from", unbounded ? "" : filter.from);
        result.put("to", unbounded ? "" : filter.to);
        result.put("items", items.subList(fromIndex, toIndex));
        result.put("total", total);
        result.put("page", safePage);
        result.put("pageSize", safeSize);
        return result;
    }

    private double value(Map<String, Double> durations, String... operations) {
        for (String operation : operations) {
            if (durations.containsKey(operation)) return durations.get(operation);
        }
        return 0D;
    }

    /** 聚合监控仪表盘所需的全部真实数据；没有数据的维度返回空数组。 */
    public Map<String, Object> dashboard(String from, String to, Long kbId, Long docId, Long conversationId) {
        Filter filter = filter(from, to, kbId, docId, conversationId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("updatedAt", OffsetDateTime.now(ZoneOffset.UTC));
        result.put("overview", overview(from, to, kbId, docId, conversationId));
        result.put("ragasEvaluation", ragasEvaluation(filter, kbId, docId, conversationId));

        Map<String, Object> document = new LinkedHashMap<>();
        Map<String, Object> docTotals = jdbc.queryForMap("SELECT COUNT(*) AS total, COUNT(*) FILTER (WHERE parse_status='SUCCESS') AS parsed FROM kb_document d WHERE d.deleted=FALSE AND d.updated_at BETWEEN ? AND ?" + docScope(kbId, docId), docArgs(filter, kbId, docId).toArray());
        document.put("total", docTotals.get("total"));
        Number parsed = (Number) docTotals.getOrDefault("parsed", 0);
        Number total = (Number) docTotals.getOrDefault("total", 0);
        document.put("parseSuccessRate", total.longValue() == 0 ? 0 : parsed.doubleValue() / total.doubleValue());
        document.put("status", jdbc.queryForList("SELECT d.chunk_status AS status, COUNT(*) AS count FROM kb_document d WHERE d.deleted=FALSE AND d.updated_at BETWEEN ? AND ?" + docScope(kbId, docId) + " GROUP BY d.chunk_status ORDER BY d.chunk_status", docArgs(filter, kbId, docId).toArray()));
        document.put("tokens", jdbc.queryForList("SELECT CASE WHEN token_count IS NULL THEN '无统计' WHEN token_count < 800 THEN '<800' WHEN token_count < 1200 THEN '800-1200' WHEN token_count < 1600 THEN '1200-1600' WHEN token_count <= 2000 THEN '1600-2000' WHEN token_count <= 2400 THEN '2000-2400' ELSE '>2400' END AS bucket, COUNT(*) AS count FROM kb_chunk c JOIN kb_document d ON d.id=c.doc_id WHERE d.deleted=FALSE AND c.created_at BETWEEN ? AND ?" + docScope(kbId, docId) + " GROUP BY bucket ORDER BY MIN(COALESCE(c.token_count,0))", docArgs(filter, kbId, docId).toArray()));
        document.put("failed", jdbc.queryForList("SELECT d.id AS \"docId\", d.file_name AS \"fileName\", d.parse_status AS \"parseStatus\", d.chunk_status AS \"chunkStatus\", LEFT(COALESCE(d.error_msg,''),500) AS reason FROM kb_document d WHERE d.deleted=FALSE AND (d.parse_status='FAILED' OR d.chunk_status='FAILED' OR d.error_msg IS NOT NULL) AND d.updated_at BETWEEN ? AND ?" + docScope(kbId, docId) + " ORDER BY d.updated_at DESC LIMIT 20", docArgs(filter, kbId, docId).toArray()));
        document.put("fallbackCount", jdbc.queryForObject("SELECT COUNT(*) FROM kb_document d WHERE d.deleted=FALSE AND d.error_msg ILIKE '%Tika%' AND d.updated_at BETWEEN ? AND ?" + docScope(kbId, docId), Long.class, docArgs(filter, kbId, docId).toArray()));
        result.put("documentQuality", document);

        Map<String, Object> retrieval = new LinkedHashMap<>();
        retrieval.put("hybrid", jdbc.queryForList("SELECT COALESCE(attributes->>'retrievalSources','unknown') AS source, COUNT(*) AS count FROM rag_observation_event " + filter.sql + " AND operation='retrieval.hybrid_rrf' GROUP BY source ORDER BY count DESC", filter.args.toArray()));
        retrieval.put("hotDocuments", jdbc.queryForList("SELECT d.id AS \"docId\", d.file_name AS \"fileName\", COUNT(*) AS count FROM kb_qa_record q CROSS JOIN LATERAL unnest(COALESCE(q.retrieved_chunk_ids, ARRAY[]::bigint[])) rid JOIN kb_chunk c ON c.id=rid JOIN kb_document d ON d.id=c.doc_id WHERE q.created_at BETWEEN ? AND ?" + qaScope(kbId, docId, conversationId) + " GROUP BY d.id,d.file_name ORDER BY count DESC LIMIT 10", qaArgs(filter, kbId, docId, conversationId).toArray()));
        retrieval.put("recallTrend", retrievalRecallTrend(filter, kbId, docId, conversationId));
        retrieval.put("similarity", List.of());
        retrieval.put("lowSimilarityQueries", List.of());
        result.put("retrievalQuality", retrieval);

        Map<String, Object> generation = new LinkedHashMap<>();
        generation.put("citation", jdbc.queryForList("SELECT CASE WHEN cardinality(COALESCE(q.retrieved_chunk_ids, ARRAY[]::bigint[])) > 0 THEN '有检索引用' ELSE '无检索引用' END AS status, COUNT(*) AS count FROM kb_qa_record q WHERE q.created_at BETWEEN ? AND ?" + qaScope(kbId, docId, conversationId) + " GROUP BY status", qaArgs(filter, kbId, docId, conversationId).toArray()));
        Number qaTotal = jdbc.queryForObject("SELECT COUNT(*) FROM kb_qa_record q WHERE q.created_at BETWEEN ? AND ?" + qaScope(kbId, docId, conversationId), Number.class, qaArgs(filter, kbId, docId, conversationId).toArray());
        Number cited = jdbc.queryForObject("SELECT COUNT(*) FROM kb_qa_record q WHERE q.created_at BETWEEN ? AND ? AND cardinality(COALESCE(q.retrieved_chunk_ids, ARRAY[]::bigint[])) > 0" + qaScope(kbId, docId, conversationId), Number.class, qaArgs(filter, kbId, docId, conversationId).toArray());
        generation.put("citationRate", qaTotal == null || qaTotal.longValue() == 0 ? 0 : cited.doubleValue() / qaTotal.doubleValue());
        generation.put("promptTokens", jdbc.queryForList("SELECT CASE WHEN (attributes->>'prompt_tokens') ~ '^[0-9]+$' THEN CASE WHEN (attributes->>'prompt_tokens')::int < 2000 THEN '<2000' WHEN (attributes->>'prompt_tokens')::int < 3500 THEN '2000-3500' WHEN (attributes->>'prompt_tokens')::int < 5000 THEN '3500-5000' WHEN (attributes->>'prompt_tokens')::int <= 6500 THEN '5000-6500' ELSE '>6500' END ELSE '未知' END AS bucket, COUNT(*) AS count FROM rag_observation_event " + filter.sql + " AND operation='llm.chat' GROUP BY bucket ORDER BY bucket", filter.args.toArray()));
        generation.put("riskSamples", List.of());
        result.put("generationQuality", generation);

        Map<String, Object> feedback = new LinkedHashMap<>();
        String feedbackFilter = " FROM kb_feedback f JOIN kb_qa_record q ON q.id=f.qa_record_id WHERE f.created_at BETWEEN ? AND ?" + qaScope(kbId, docId, conversationId);
        List<Object> feedbackArgs = qaArgs(filter, kbId, docId, conversationId);
        feedback.put("distribution", jdbc.queryForList("SELECT CASE WHEN f.rating=1 THEN '点赞' WHEN f.rating=-1 THEN '点踩' ELSE '无反馈' END AS status, COUNT(*) AS count" + feedbackFilter + " GROUP BY status", feedbackArgs.toArray()));
        Number feedbackTotal = jdbc.queryForObject("SELECT COUNT(*)" + feedbackFilter, Number.class, feedbackArgs.toArray());
        Number likes = jdbc.queryForObject("SELECT COUNT(*)" + feedbackFilter + " AND f.rating=1", Number.class, feedbackArgs.toArray());
        Number dislikes = jdbc.queryForObject("SELECT COUNT(*)" + feedbackFilter + " AND f.rating=-1", Number.class, feedbackArgs.toArray());
        feedback.put("likeRate", feedbackTotal == null || feedbackTotal.longValue() == 0 ? 0 : likes.doubleValue() / feedbackTotal.doubleValue());
        feedback.put("dislikeRate", feedbackTotal == null || feedbackTotal.longValue() == 0 ? 0 : dislikes.doubleValue() / feedbackTotal.doubleValue());
        feedback.put("trend", jdbc.queryForList("SELECT to_char(date_trunc('day', f.created_at),'MM-DD') AS day, SUM(CASE WHEN f.rating=1 THEN 1 ELSE 0 END) AS likes, SUM(CASE WHEN f.rating=-1 THEN 1 ELSE 0 END) AS dislikes" + feedbackFilter + " GROUP BY day ORDER BY day", feedbackArgs.toArray()));
        feedback.put("reasons", List.of());
        feedback.put("knowledgeBases", List.of());
        result.put("feedback", feedback);
        result.put("alerts", alerts(filter, kbId, docId));
        return result;
    }

    /**
     * 聚合 RAGAS 原始评分。所有分数都规范到 [0,1]；无有效样本时保留 null，
     * 让前端明确展示“暂无评估”，而不是用 0 冒充低分。
     */
    private Map<String, Object> ragasEvaluation(Filter filter, Long kbId, Long docId, Long conversationId) {
        String where = evaluationWhere(filter, kbId, docId, conversationId);
        List<Object> args = evaluationArgs(filter, kbId, docId, conversationId);
        String faithfulness = normalizedMetric("faithfulness");
        String answerRelevancy = normalizedMetric("answer_relevancy", "answer_relevance", "response_relevancy", "answer_correctness");
        String contextPrecision = normalizedMetric("context_precision");
        String contextRecall = normalizedMetric("context_recall");
        Map<String, Object> run = jdbc.queryForMap(
                "SELECT COUNT(*) FILTER (WHERE t.status='SUCCESS') AS evaluated, " +
                        "COUNT(*) FILTER (WHERE t.status IN ('SUCCESS','FAILED')) AS completed, " +
                        "COUNT(*) FILTER (WHERE t.status='FAILED') AS failed, " +
                        "MAX(t.updated_at) FILTER (WHERE t.status='SUCCESS') AS \"lastRunAt\", " +
                        "COALESCE(SUM(CASE WHEN t.status='SUCCESS' " +
                        "AND " + faithfulness + ">=0.80 " +
                        "AND " + answerRelevancy + ">=0.75 " +
                        "AND " + contextPrecision + ">=0.70 " +
                        "AND " + contextRecall + ">=0.70 THEN 1 ELSE 0 END),0) AS passed " +
                        "FROM rag_evaluation_task t JOIN kb_qa_record q ON q.id=t.qa_record_id " + where,
                args.toArray());

        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("faithfulness", ragasMetricSummary(where, args, faithfulness, 0.80));
        metrics.put("answerRelevancy", ragasMetricSummary(where, args, answerRelevancy, 0.75));
        metrics.put("contextPrecision", ragasMetricSummary(where, args, contextPrecision, 0.70));
        metrics.put("contextRecall", ragasMetricSummary(where, args, contextRecall, 0.70));

        List<Map<String, Object>> trend = jdbc.queryForList(
                "SELECT to_char(date_trunc('day', t.updated_at),'MM-DD') AS period, " +
                        "AVG(" + faithfulness + ") AS faithfulness, " +
                        "AVG(" + answerRelevancy + ") AS \"answerRelevancy\", " +
                        "AVG(" + contextPrecision + ") AS \"contextPrecision\", " +
                        "AVG(" + contextRecall + ") AS \"contextRecall\" " +
                        "FROM rag_evaluation_task t JOIN kb_qa_record q ON q.id=t.qa_record_id " + where +
                        " AND t.status='SUCCESS' GROUP BY date_trunc('day', t.updated_at) " +
                        "ORDER BY date_trunc('day', t.updated_at)",
                args.toArray());

        Map<String, Object> result = new LinkedHashMap<>(run);
        result.put("metrics", metrics);
        result.put("trend", trend);
        result.put("from", filter.from);
        result.put("to", filter.to);
        return result;
    }

    private Map<String, Object> ragasMetricSummary(String where, List<Object> args, String expression, double threshold) {
        Map<String, Object> summary = jdbc.queryForMap(
                "SELECT COUNT(" + expression + ") AS samples, AVG(" + expression + ") AS average, " +
                        "MIN(" + expression + ") AS min, MAX(" + expression + ") AS max " +
                        "FROM rag_evaluation_task t JOIN kb_qa_record q ON q.id=t.qa_record_id " + where +
                        " AND t.status='SUCCESS'", args.toArray());
        summary.put("threshold", threshold);
        return summary;
    }

    private Map<String, Object> performance(List<Map<String, Object>> stages, Filter filter) {
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("doclingErrorRate", errorRate(stages, "document.parse"));
        metrics.put("embeddingErrorRate", errorRate(stages, "embedding.dense", "embedding.sparse_bm25"));
        metrics.put("milvusErrorRate", errorRate(stages, "vector.upsert", "retrieval.hybrid_rrf"));
        metrics.put("rerankErrorRate", errorRate(stages, "llm.rerank"));
        metrics.put("llmErrorRate", errorRate(stages, "llm.chat"));
        Number todayQa = jdbc.queryForObject(
                "SELECT COUNT(*) FROM rag_observation_event " + filter.sql +
                        " AND operation='llm.chat' AND created_at >= CURRENT_DATE",
                Number.class, filter.args.toArray());
        metrics.put("todayQps", todayQa == null ? 0 : todayQa.longValue());
        return metrics;
    }

    private double errorRate(List<Map<String, Object>> stages, String... operations) {
        long calls = 0;
        double failures = 0;
        for (Map<String, Object> stage : stages) {
            boolean matched = false;
            for (String operation : operations) {
                if (operation.equals(stage.get("operation"))) {
                    matched = true;
                    break;
                }
            }
            if (matched) {
                Number stageCalls = (Number) stage.get("calls");
                Number success = (Number) stage.get("success_rate");
                long count = stageCalls == null ? 0L : stageCalls.longValue();
                calls += count;
                if (success != null) failures += count * Math.max(0D, 1D - success.doubleValue() / 100D);
            }
        }
        return calls == 0 ? 0D : failures * 100D / calls;
    }

    private List<Map<String, Object>> alerts(Filter filter, Long kbId, Long docId) {
        List<Map<String, Object>> alerts = new ArrayList<>();
        Number failures = jdbc.queryForObject("SELECT COUNT(*) FROM rag_observation_event " + filter.sql + " AND success=FALSE", Number.class, filter.args.toArray());
        if (failures != null && failures.longValue() > 0) alerts.add(Map.of("severity", "danger", "title", "处理链路存在失败事件", "count", failures.longValue()));
        Number docs = jdbc.queryForObject("SELECT COUNT(*) FROM kb_document d WHERE d.deleted=FALSE AND (d.parse_status='FAILED' OR d.chunk_status='FAILED') AND d.updated_at BETWEEN ? AND ?" + docScope(kbId, docId), Number.class, docArgs(filter, kbId, docId).toArray());
        if (docs != null && docs.longValue() > 0) alerts.add(Map.of("severity", "warning", "title", "文档解析或索引失败", "count", docs.longValue()));
        return alerts;
    }

    private String docScope(Long kbId, Long docId) { StringBuilder s=new StringBuilder(); if(kbId!=null)s.append(" AND d.kb_id=?"); if(docId!=null)s.append(" AND d.id=?"); return s.toString(); }
    private List<Object> docArgs(Filter f, Long kbId, Long docId) { List<Object> a=new ArrayList<>(List.of(f.from,f.to)); if(kbId!=null)a.add(kbId); if(docId!=null)a.add(docId); return a; }
    private String qaScope(Long kbId, Long docId, Long conversationId) { StringBuilder s=new StringBuilder(); if(kbId!=null)s.append(" AND ? = ANY(q.kb_ids)"); if(docId!=null)s.append(" AND EXISTS (SELECT 1 FROM kb_chunk cx WHERE cx.id=ANY(q.retrieved_chunk_ids) AND cx.doc_id=? )"); if(conversationId!=null)s.append(" AND q.conversation_id=?"); return s.toString(); }
    private List<Object> qaArgs(Filter f, Long kbId, Long docId, Long conversationId) { List<Object> a=new ArrayList<>(List.of(f.from,f.to)); if(kbId!=null)a.add(kbId); if(docId!=null)a.add(docId); if(conversationId!=null)a.add(conversationId); return a; }

    private Filter filter(String from, String to, Long kbId, Long docId, Long conversationId) {
        OffsetDateTime end = parse(to, OffsetDateTime.now(ZoneOffset.UTC));
        OffsetDateTime start = parse(from, end.minusDays(1));
        StringBuilder sql = new StringBuilder("WHERE created_at BETWEEN ? AND ?");
        List<Object> args = new ArrayList<>(List.of(start, end));
        if (kbId != null) { sql.append(" AND kb_id=?"); args.add(kbId); }
        if (docId != null) { sql.append(" AND doc_id=?"); args.add(docId); }
        if (conversationId != null) { sql.append(" AND conversation_id=?"); args.add(conversationId); }
        return new Filter(sql.toString(), args, start, end);
    }

    private String qualityWhere(Filter filter, Long kbId, Long docId, Long conversationId) {
        StringBuilder where = new StringBuilder("WHERE t.created_at BETWEEN ? AND ?");
        if (kbId != null) where.append(" AND q.kb_ids @> ARRAY[?]::bigint[]");
        if (docId != null) where.append(" AND EXISTS (SELECT 1 FROM kb_chunk c WHERE c.id = ANY(q.retrieved_chunk_ids) AND c.doc_id=?)");
        if (conversationId != null) where.append(" AND q.conversation_id=?");
        return where.toString();
    }

    /** RAGAS 面板按评估完成时间过滤，避免待处理任务被计入评估窗口。 */
    private String evaluationWhere(Filter filter, Long kbId, Long docId, Long conversationId) {
        StringBuilder where = new StringBuilder("WHERE t.updated_at BETWEEN ? AND ?");
        if (kbId != null) where.append(" AND q.kb_ids @> ARRAY[?]::bigint[]");
        if (docId != null) where.append(" AND EXISTS (SELECT 1 FROM kb_chunk c WHERE c.id = ANY(q.retrieved_chunk_ids) AND c.doc_id=?)");
        if (conversationId != null) where.append(" AND q.conversation_id=?");
        return where.toString();
    }

    private List<Object> evaluationArgs(Filter filter, Long kbId, Long docId, Long conversationId) {
        return qualityArgs(filter, kbId, docId, conversationId);
    }

    private List<Object> qualityArgs(Filter filter, Long kbId, Long docId, Long conversationId) {
        List<Object> args = new ArrayList<>(List.of(filter.from, filter.to));
        if (kbId != null) args.add(kbId);
        if (docId != null) args.add(docId);
        if (conversationId != null) args.add(conversationId);
        return args;
    }

    private String metric(String key) {
        return "CASE WHEN t.metrics->>'" + key + "' ~ '^[0-9]+(\\.[0-9]+)?$' THEN (t.metrics->>'" + key + "')::numeric END";
    }

    private String normalizedMetric(String... keys) {
        StringBuilder raw = new StringBuilder("COALESCE(");
        for (int index = 0; index < keys.length; index++) {
            if (index > 0) raw.append(",");
            raw.append(metric(keys[index]));
        }
        raw.append(")");
        String value = raw.toString();
        return "CASE WHEN " + value + " IS NULL THEN NULL " +
                "WHEN " + value + " > 1 AND " + value + " <= 100 THEN " + value + " / 100 " +
                "ELSE LEAST(GREATEST(" + value + ",0),1) END";
    }

    /**
     * 读取离线评估服务产生的召回率。优先采用 Recall@5，兼容历史任务中的
     * Recall@K、通用 recall 以及 RAGAS 的上下文召回率字段。
     */
    private String recallMetricExpression() {
        return "CASE " +
                "WHEN t.metrics->>'recall_at_5' ~ '^[0-9]+(\\.[0-9]+)?$' THEN (t.metrics->>'recall_at_5')::numeric " +
                "WHEN t.metrics->>'recallAt5' ~ '^[0-9]+(\\.[0-9]+)?$' THEN (t.metrics->>'recallAt5')::numeric " +
                "WHEN t.metrics->>'recall_at_k' ~ '^[0-9]+(\\.[0-9]+)?$' THEN (t.metrics->>'recall_at_k')::numeric " +
                "WHEN t.metrics->>'recallAtK' ~ '^[0-9]+(\\.[0-9]+)?$' THEN (t.metrics->>'recallAtK')::numeric " +
                "WHEN t.metrics->>'recall' ~ '^[0-9]+(\\.[0-9]+)?$' THEN (t.metrics->>'recall')::numeric " +
                "WHEN t.metrics->>'context_recall' ~ '^[0-9]+(\\.[0-9]+)?$' THEN (t.metrics->>'context_recall')::numeric " +
                "END";
    }

    private List<Map<String, Object>> retrievalRecallTrend(Filter filter, Long kbId, Long docId,
                                                            Long conversationId) {
        return jdbc.queryForList(
                "SELECT to_char(date_trunc('day', t.created_at),'MM-DD') AS period, " +
                        "COALESCE(AVG(" + recallMetricExpression() + "),0) AS \"recallAt5\" " +
                        "FROM rag_evaluation_task t JOIN kb_qa_record q ON q.id=t.qa_record_id " +
                        qualityWhere(filter, kbId, docId, conversationId) +
                        " AND t.status='SUCCESS' GROUP BY date_trunc('day', t.created_at) " +
                        "ORDER BY date_trunc('day', t.created_at)",
                qualityArgs(filter, kbId, docId, conversationId).toArray());
    }

    private double toRatio(Number raw) {
        if (raw == null) return 0D;
        double value = raw.doubleValue();
        if (!Double.isFinite(value)) return 0D;
        double ratio = value > 1D && value <= 100D ? value / 100D : value;
        return Math.max(0D, Math.min(1D, ratio));
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private OffsetDateTime parse(String value, OffsetDateTime fallback) {
        if (value == null || value.isBlank()) return fallback;
        try {
            return OffsetDateTime.parse(value);
        } catch (DateTimeParseException ignored) {
            try {
                return LocalDateTime.parse(value, DISPLAY_DATE_TIME)
                        .atZone(DISPLAY_ZONE)
                        .toOffsetDateTime();
            } catch (DateTimeParseException invalidValue) {
                return fallback;
            }
        }
    }
    private record Filter(String sql, List<Object> args, OffsetDateTime from, OffsetDateTime to) { }
}
