package com.example.rag.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.*;

@Service
public class QdrantService {
    private final RestClient client;
    private final String collection;
    private final double scoreThreshold;

    public QdrantService(@Value("${qdrant.host:localhost}") String host, @Value("${qdrant.port:6333}") int port, @Value("${qdrant.collection:kb_embeddings}") String collection, @Value("${qdrant.score-threshold:0.20}") double scoreThreshold) {
        this.collection = collection;
        this.scoreThreshold = Math.max(0D, Math.min(1D, scoreThreshold));
        this.client = RestClient.builder().baseUrl("http://" + host + ":" + port).build();
        ensureCollection();
    }

    private void ensureCollection() {
        try {
            client.put().uri("/collections/" + collection).contentType(MediaType.APPLICATION_JSON).body(Map.of("vectors", Map.of("size", 1024, "distance", "Cosine"))).retrieve().toBodilessEntity();
        } catch (Exception ignored) {
        }
        ensurePayloadIndex("kb_id", "integer");
        ensurePayloadIndex("doc_id", "integer");
        ensurePayloadIndex("dept_id", "integer");
    }

    private void ensurePayloadIndex(String fieldName, String fieldSchema) {
        try {
            client.put().uri("/collections/" + collection + "/index")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("field_name", fieldName, "field_schema", fieldSchema))
                    .retrieve().toBodilessEntity();
        } catch (Exception ignored) {
            // Qdrant may still be starting when the application initializes.
        }
    }

    public void upsert(long id, List<Float> vector, Map<String, Object> payload) {
        client.put().uri("/collections/" + collection + "/points?wait=true").contentType(MediaType.APPLICATION_JSON).body(Map.of("points", List.of(Map.of("id", id, "vector", vector, "payload", payload)))).retrieve().toBodilessEntity();
    }

    public void deleteByDocument(long docId) {
        Map<String, Object> filter = Map.of("must", List.of(Map.of("key", "doc_id", "match", Map.of("value", docId))));
        client.post().uri("/collections/" + collection + "/points/delete?wait=true")
                .contentType(MediaType.APPLICATION_JSON).body(Map.of("filter", filter)).retrieve().toBodilessEntity();
    }

    public List<Map<String, Object>> search(List<Float> vector, int limit) {
        return search(vector, limit, List.of());
    }

    public List<Map<String, Object>> search(List<Float> vector, int limit, List<Long> allowedKbIds) {
        return search(vector, limit, allowedKbIds, scoreThreshold);
    }

    /** Use a lower threshold only when the primary search produced no candidates. */
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
        if (allowedKbIds != null) body.put("filter", Map.of("must", List.of(Map.of("key", "kb_id", "match", Map.of("any", allowedKbIds)))));
        Map<?, ?> response = client.post().uri("/collections/" + collection + "/points/search").contentType(MediaType.APPLICATION_JSON).body(body).retrieve().body(Map.class);
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
        if (value instanceof Number number) score = number.doubleValue();
        else {
            try {
                score = Double.parseDouble(String.valueOf(value));
            } catch (NumberFormatException ignored) {
                return false;
            }
        }
        return Double.isFinite(score) && score >= threshold;
    }
}
