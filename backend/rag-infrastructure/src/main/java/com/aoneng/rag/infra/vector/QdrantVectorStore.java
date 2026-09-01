package com.aoneng.rag.infra.vector;

import com.aoneng.rag.infra.config.QdrantProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Qdrant 向量存储服务实现。
 * Qdrant HTTP API 的轻量 REST 封装。
 */
@Component
public class QdrantVectorStore implements VectorStore {

    private static final Logger log = LoggerFactory.getLogger(QdrantVectorStore.class);

    private final RestClient client;
    private final String collection;
    private final double scoreThreshold;

    public QdrantVectorStore(QdrantProperties properties) {
        this.collection = properties.collection();
        this.scoreThreshold = Math.max(0D, Math.min(1D, properties.scoreThreshold()));
        this.client = RestClient.builder()
                .baseUrl("http://" + properties.host() + ":" + properties.port())
                .build();
    }

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

    @Override
    public void upsert(long id, List<Float> vector, Map<String, Object> payload) {
        client.put().uri("/collections/" + collection + "/points?wait=true")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("points", List.of(Map.of("id", id, "vector", vector, "payload", payload))))
                .retrieve().toBodilessEntity();
    }

    @Override
    public void deleteByDocument(long docId) {
        Map<String, Object> filter = Map.of("must",
                List.of(Map.of("key", "doc_id", "match", Map.of("value", docId))));
        client.post().uri("/collections/" + collection + "/points/delete?wait=true")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("filter", filter))
                .retrieve().toBodilessEntity();
    }

    @Override
    public List<Map<String, Object>> search(List<Float> vector, int limit) {
        return search(vector, limit, List.of());
    }

    @Override
    public List<Map<String, Object>> search(List<Float> vector, int limit, List<Long> allowedKbIds) {
        return search(vector, limit, allowedKbIds, scoreThreshold);
    }

    @Override
    public List<Map<String, Object>> searchAdaptive(List<Float> vector, int limit, List<Long> allowedKbIds) {
        List<Map<String, Object>> primary = search(vector, limit, allowedKbIds, scoreThreshold);
        if (!primary.isEmpty() || scoreThreshold <= 0.05D) return primary;
        return search(vector, limit, allowedKbIds, Math.max(0.05D, scoreThreshold * 0.5D));
    }

    private List<Map<String, Object>> search(List<Float> vector, int limit, List<Long> allowedKbIds, double threshold) {
        if (allowedKbIds != null && allowedKbIds.isEmpty()) return List.of();
        Map<String, Object> body = new HashMap<>();
        body.put("vector", vector);
        body.put("limit", limit);
        body.put("with_payload", true);
        body.put("score_threshold", threshold);
        if (allowedKbIds != null) {
            body.put("filter", Map.of("must",
                    List.of(Map.of("key", "kb_id", "match", Map.of("any", allowedKbIds)))));
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

    private boolean meetsScoreThreshold(Map<String, Object> hit, double threshold) {
        Object value = hit.get("score");
        double score;
        if (value instanceof Number number) {
            score = number.doubleValue();
        } else {
            try {
                score = Double.parseDouble(String.valueOf(value));
            } catch (NumberFormatException e) {
                return false;
            }
        }
        return Double.isFinite(score) && score >= threshold;
    }
}
