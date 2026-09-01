package com.example.rag.framework.client;

import com.example.rag.framework.config.QdrantProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Qdrant HTTP REST 客户端封装。仅负责协议层交互（集合管理、向量写入、检索、payload 索引）；
 * 权限过滤、payload 拼装、重排等业务职责放在 rag-biz 层，不在本类承担。
 */
@Component
public class QdrantVectorClient {
    private static final Logger log = LoggerFactory.getLogger(QdrantVectorClient.class);

    private final QdrantProperties properties;
    private final RestClient http;

    public QdrantVectorClient(QdrantProperties properties) {
        this.properties = properties;
        this.http = RestClient.builder().baseUrl("http://" + properties.host() + ":" + properties.port()).build();
    }

    /**
     * 当前 Qdrant collection 名称。
     */
    public String collection() {
        return properties.collection();
    }

    /**
     * 确保 collection 已创建并把常用 payload 字段（kb_id / doc_id / dept_id）建好索引。
     * 幂等操作，可在启动时调用。Qdrant 未就绪时记录调试日志，不阻塞启动。
     *
     * @param vectorSize 向量维度，必须与 {@code dashscope.embedding-dimensions} 一致
     */
    public void ensureCollection(int vectorSize) {
        try {
            http.put().uri("/collections/" + properties.collection())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("vectors", Map.of("size", vectorSize, "distance", "Cosine")))
                    .retrieve()
                    .toBodilessEntity();
            log.info("Ensured Qdrant collection {}", properties.collection());
        } catch (Exception e) {
            log.debug("Qdrant collection bootstrap skipped: {}", e.getMessage());
        }
        ensurePayloadIndex("kb_id", "integer");
        ensurePayloadIndex("doc_id", "integer");
        ensurePayloadIndex("dept_id", "integer");
    }

    /**
     * 为指定 payload 字段创建索引，便于按 kb_id / doc_id / dept_id 高效过滤。
     * 失败时静默忽略（启动时 Qdrant 可能尚未就绪）。
     */
    public void ensurePayloadIndex(String fieldName, String fieldSchema) {
        try {
            http.put().uri("/collections/" + properties.collection() + "/index")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("field_name", fieldName, "field_schema", fieldSchema))
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception ignored) {
            // Qdrant may still be starting when the application initializes.
        }
    }

    /**
     * Upsert points with payload. The caller is responsible for shaping payloads.
     */
    /**
     * 批量写入或更新向量点（带 payload）。空列表直接返回。
     *
     * @param points 待写入的向量点，至少包含 id、vector、payload
     */
    public void upsert(List<PointInput> points) {
        if (points == null || points.isEmpty()) return;
        Map<String, Object> body = Map.of("points", points.stream().map(PointInput::toJson).toList());
        http.put().uri("/collections/" + properties.collection() + "/points?wait=true")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toBodilessEntity();
    }

    /**
     * 按 doc_id 过滤删除该文档的全部向量点，用于文档删除或重建索引。
     * 删除失败仅记录告警，不抛出以避免阻断主流程。
     */
    public void deleteByDocId(long docId) {
        Map<String, Object> filter = Map.of("must", List.of(Map.of("key", "doc_id", "match", Map.of("value", docId))));
        try {
            http.post().uri("/collections/" + properties.collection() + "/points/delete?wait=true")
                    .contentType(MediaType.APPLICATION_JSON).body(Map.of("filter", filter))
                    .retrieve().toBodilessEntity();
        } catch (Exception e) {
            log.warn("Qdrant delete by doc_id {} failed: {}", docId, e.getMessage());
        }
    }

    /**
     * Search by vector with optional payload filter. Returns up to {@code limit} hits.
     */
    /**
     * 按向量相似度检索。可选 filter 用于权限 / 知识库 / 文档范围限制。
     *
     * @param vector 查询向量
     * @param limit  返回的最大条数
     * @param filter Qdrant 过滤条件，传 {@code null} 表示不过滤
     * @return 命中列表，按相似度降序
     */
    public List<VectorHit> search(List<Float> vector, int limit, Map<String, Object> filter) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("vector", vector);
        body.put("limit", limit);
        body.put("with_payload", true);
        if (filter != null) body.put("filter", filter);

        @SuppressWarnings("unchecked")
        Map<String, Object> response = http.post()
                .uri("/collections/" + properties.collection() + "/points/search")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(Map.class);

        Object result = response == null ? null : response.get("result");
        if (!(result instanceof List<?> list)) return List.of();

        List<VectorHit> hits = new ArrayList<>(list.size());
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> hit)) continue;
            Object idObj = hit.get("id");
            Object scoreObj = hit.get("score");
            Object payloadObj = hit.get("payload");
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = payloadObj instanceof Map<?, ?> p
                    ? (Map<String, Object>) p : Map.of();
            double score = scoreObj instanceof Number n ? n.doubleValue() : 0d;
            hits.add(new VectorHit(String.valueOf(Objects.requireNonNullElse(idObj, "0")),
                    score, payload));
        }
        return hits;
    }

    /**
     * {@link #upsert(List)} 的输入参数封装。
     *
     * @param id      Qdrant 点 ID（与 PostgreSQL kb_chunk 主键保持稳定映射）
     * @param vector  嵌入向量，长度必须与 collection 的 {@code vectors.size} 一致
     * @param payload 业务 payload，至少包含 chunk_id、doc_id、kb_id、content、file_name、visibility、dept_id
     */
    public record PointInput(long id, List<Float> vector, Map<String, Object> payload) {

        /**
         * 转换为 Qdrant REST 接口要求的 JSON 结构。
         */
        public Map<String, Object> toJson() {
            Map<String, Object> json = new LinkedHashMap<>();
            json.put("id", id);
            json.put("vector", vector);
            json.put("payload", payload);
            return json;
        }
    }
}
