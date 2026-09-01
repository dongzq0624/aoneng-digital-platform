package com.example.rag.service;

import com.example.rag.chat.service.QdrantService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Qdrant 向量存储服务默认实现。
 * Qdrant HTTP API 的轻量 REST 封装。构造时初始化 collection
 * （为 {@code kb_id}、{@code doc_id}、{@code dept_id} 创建 payload 索引）。
 *
 * <p>应用就绪后异步初始化向量集合，如果 Qdrant 启动较慢会记录错误但不影响其他功能。</p>
 */
@Service
public class QdrantServiceImpl implements QdrantService {

    private static final Logger log = LoggerFactory.getLogger(QdrantServiceImpl.class);

    private final RestClient client;
    private final String collection;
    private final double scoreThreshold;

    public QdrantServiceImpl(@Value("${qdrant.host:localhost}") String host,
                             @Value("${qdrant.port:6333}") int port,
                             @Value("${qdrant.collection:kb_embeddings}") String collection,
                             @Value("${qdrant.score-threshold:0.20}") double scoreThreshold) {
        this.collection = collection;
        this.scoreThreshold = Math.max(0D, Math.min(1D, scoreThreshold));
        this.client = RestClient.builder().baseUrl("http://" + host + ":" + port).build();
    }

    /**
     * 应用就绪后异步初始化 collection 及 payload 索引。
     * Qdrant 启动较慢时会在日志中记录错误，不会阻塞应用启动。
     */
    @EventListener(ApplicationReadyEvent.class)
    @Async
    public void onApplicationReady() {
        log.info("正在初始化 Qdrant 集合: {}", collection);
        ensureCollection();
        ensurePayloadIndex("kb_id", "integer");
        ensurePayloadIndex("doc_id", "integer");
        ensurePayloadIndex("dept_id", "integer");
        log.info("Qdrant 集合初始化完成: {}", collection);
    }

    /**
     * 初始化 collection，并创建向量配置。
     */
    private void ensureCollection() {
        try {
            client.put().uri("/collections/" + collection).contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("vectors", Map.of("size", 1024, "distance", "Cosine")))
                    .retrieve().toBodilessEntity();
            log.debug("Qdrant 集合创建成功: {}", collection);
        } catch (Exception e) {
            log.error("Qdrant 集合初始化失败，集合={}，错误={}，请检查 Qdrant 服务是否正常运行",
                    collection, e.getMessage());
        }
    }

    /**
     * 为 payload 字段创建索引，便于按 kb_id / doc_id / dept_id 高效过滤。
     */
    private void ensurePayloadIndex(String fieldName, String fieldSchema) {
        try {
            client.put().uri("/collections/" + collection + "/index")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("field_name", fieldName, "field_schema", fieldSchema))
                    .retrieve().toBodilessEntity();
            log.debug("Qdrant payload 索引创建成功: {}", fieldName);
        } catch (Exception e) {
            log.warn("Qdrant payload 索引创建失败（可能已存在），field={}, error={}",
                    fieldName, e.getMessage());
        }
    }

    /**
     * 写入或更新向量点。
     */
    @Override
    public void upsert(long id, List<Float> vector, Map<String, Object> payload) {
        client.put().uri("/collections/" + collection + "/points?wait=true")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("points", List.of(Map.of("id", id, "vector", vector, "payload", payload))))
                .retrieve().toBodilessEntity();
    }

    /**
     * 按文档 ID 删除该文档的全部向量点。
     */
    @Override
    public void deleteByDocument(long docId) {
        Map<String, Object> filter = Map.of("must", List.of(Map.of("key", "doc_id", "match", Map.of("value", docId))));
        client.post().uri("/collections/" + collection + "/points/delete?wait=true")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("filter", filter))
                .retrieve().toBodilessEntity();
    }

    /**
     * 在全部范围内执行向量检索。
     */
    @Override
    public List<Map<String, Object>> search(List<Float> vector, int limit) {
        return search(vector, limit, List.of());
    }

    /**
     * 在指定知识库范围内执行向量检索，使用配置的默认阈值。
     */
    @Override
    public List<Map<String, Object>> search(List<Float> vector, int limit, List<Long> allowedKbIds) {
        return search(vector, limit, allowedKbIds, scoreThreshold);
    }

    /**
     * 低召回降级自适应检索：当主检索无结果时自动降低阈值重新检索。
     */
    @Override
    public List<Map<String, Object>> searchAdaptive(List<Float> vector, int limit, List<Long> allowedKbIds) {
        List<Map<String, Object>> primary = search(vector, limit, allowedKbIds, scoreThreshold);
        if (!primary.isEmpty() || scoreThreshold <= 0.05D) return primary;
        return search(vector, limit, allowedKbIds, Math.max(0.05D, scoreThreshold * 0.5D));
    }

    /**
     * 执行带过滤条件的向量检索。
     *
     * @param vector       查询向量
     * @param limit       返回的最大条数
     * @param allowedKbIds 允许的知识库 ID 列表（空列表直接返回空结果）
     * @param threshold   相似度阈值
     * @return 命中列表
     */
    private List<Map<String, Object>> search(List<Float> vector, int limit, List<Long> allowedKbIds, double threshold) {
        if (allowedKbIds != null && allowedKbIds.isEmpty()) return List.of();
        Map<String, Object> body = new HashMap<>();
        body.put("vector", vector);
        body.put("limit", limit);
        body.put("with_payload", true);
        body.put("score_threshold", threshold);
        if (allowedKbIds != null) {
            body.put("filter", Map.of("must", List.of(Map.of("key", "kb_id", "match", Map.of("any", allowedKbIds)))));
        }
        Map<?, ?> response = client.post().uri("/collections/" + collection + "/points/search")
                .contentType(MediaType.APPLICATION_JSON).body(body)
                .retrieve().body(Map.class);
        Object result = response == null ? null : response.get("result");
        return result instanceof List<?> list ? list.stream()
                .filter(Map.class::isInstance)
                .map(x -> (Map<String, Object>) x)
                .filter(hit -> meetsScoreThreshold(hit, threshold))
                .toList() : List.of();
    }

    /**
     * 校验命中分数是否达到阈值。
     */
    private boolean meetsScoreThreshold(Map<String, Object> hit, double threshold) {
        Object value = hit.get("score");
        double score;
        if (value instanceof Number number) score = number.doubleValue();
        else {
            try {
                score = Double.parseDouble(String.valueOf(value));
            } catch (NumberFormatException e) {
                // 分数格式异常时视为不达标
                return false;
            }
        }
        return Double.isFinite(score) && score >= threshold;
    }
}
