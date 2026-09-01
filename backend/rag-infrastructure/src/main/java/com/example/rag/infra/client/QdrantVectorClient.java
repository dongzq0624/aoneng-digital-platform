package com.example.rag.infra.client;

import com.example.rag.infra.config.QdrantProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import java.util.*;

@Component
public class QdrantVectorClient {
    private static final Logger log = LoggerFactory.getLogger(QdrantVectorClient.class);

    private final QdrantProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public QdrantVectorClient(QdrantProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.create();
    }

    private String baseUrl() {
        return "http://" + properties.host() + ":" + properties.port();
    }

    public void upsertPoint(long id, List<Float> vector, Map<String, Object> payload) {
        try {
            String url = baseUrl() + "/collections/" + properties.collection() + "/points";
            Map<String, Object> point = new HashMap<>();
            point.put("id", id);
            point.put("vector", vector);
            point.put("payload", payload);
            Map<String, Object> body = Map.of("points", List.of(point));
            restClient.post().uri(url)
                .body(objectMapper.writeValueAsString(body))
                .header("Content-Type", "application/json")
                .retrieve().toEntity(String.class);
            log.debug("Upsert point success: id={}", id);
        } catch (Exception e) {
            log.error("Upsert point failed: id={}", id, e);
            throw new RuntimeException("Qdrant upsert 失败: " + e.getMessage(), e);
        }
    }

    public void deleteByFilter(Map<String, Object> filter) {
        try {
            String url = baseUrl() + "/collections/" + properties.collection() + "/points/delete";
            Map<String, Object> body = new HashMap<>();
            body.put("filter", filter);
            restClient.post().uri(url)
                .body(objectMapper.writeValueAsString(body))
                .header("Content-Type", "application/json")
                .retrieve().toEntity(String.class);
            log.debug("Delete by filter success");
        } catch (Exception e) {
            log.error("Delete by filter failed", e);
            throw new RuntimeException("Qdrant delete 失败: " + e.getMessage(), e);
        }
    }

    public List<Map<String, Object>> search(List<Float> vector, int limit, Map<String, Object> filter) {
        try {
            String url = baseUrl() + "/collections/" + properties.collection() + "/points/search";
            Map<String, Object> body = new HashMap<>();
            body.put("vector", vector);
            body.put("limit", limit);
            body.put("score_threshold", properties.scoreThreshold());
            if (filter != null && !filter.isEmpty()) {
                body.put("filter", filter);
            }
            String response = restClient.post().uri(url)
                .body(objectMapper.writeValueAsString(body))
                .header("Content-Type", "application/json")
                .retrieve().body(String.class);
            List<Map<String, Object>> results = new ArrayList<>();
            JsonNode root = objectMapper.readTree(response);
            JsonNode resultsNode = root.get("result");
            if (resultsNode != null && resultsNode.isArray()) {
                for (JsonNode point : resultsNode) {
                    Map<String, Object> item = new HashMap<>();
                    item.put("id", point.get("id").asLong());
                    item.put("score", point.get("score").asDouble());
                    item.put("payload", objectMapper.convertValue(point.get("payload"), Map.class));
                    results.add(item);
                }
            }
            return results;
        } catch (Exception e) {
            log.error("Search failed", e);
            throw new RuntimeException("Qdrant search 失败: " + e.getMessage(), e);
        }
    }

    public List<Map<String, Object>> search(List<Float> vector, int limit) {
        return search(vector, limit, null);
    }

    public void createCollectionIfNotExists(int vectorSize) {
        try {
            String url = baseUrl() + "/collections/" + properties.collection();
            restClient.get().uri(url).retrieve().body(String.class);
            log.debug("Collection {} already exists", properties.collection());
        } catch (Exception e) {
            log.info("Collection {} not found, creating", properties.collection());
            try {
                String createUrl = baseUrl() + "/collections/" + properties.collection();
                Map<String, Object> body = new HashMap<>();
                Map<String, Object> vectors = new HashMap<>();
                vectors.put("size", vectorSize);
                vectors.put("distance", "Cosine");
                body.put("vectors", vectors);
                restClient.put().uri(createUrl)
                    .body(objectMapper.writeValueAsString(body))
                    .header("Content-Type", "application/json")
                    .retrieve().toEntity(String.class);
                log.info("Collection {} created successfully", properties.collection());
            } catch (Exception createErr) {
                log.error("Failed to create collection {}", properties.collection(), createErr);
                throw new RuntimeException("Qdrant 创建 collection 失败: " + createErr.getMessage(), createErr);
            }
        }
    }
}
