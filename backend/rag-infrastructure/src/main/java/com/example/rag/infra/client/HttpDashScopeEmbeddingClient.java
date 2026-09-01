package com.example.rag.infra.client;

import com.example.rag.infra.config.DashScopeProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

@Component
public class HttpDashScopeEmbeddingClient implements DashScopeEmbeddingClient {
    private static final Logger log = LoggerFactory.getLogger(HttpDashScopeEmbeddingClient.class);
    private static final String EMBEDDINGS_URL = "/embeddings";

    private final DashScopeProperties props;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public HttpDashScopeEmbeddingClient(DashScopeProperties props, ObjectMapper objectMapper) {
        this.props = props;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    @Override
    public EmbeddingVector embed(String text) {
        return embedAll(List.of(text)).stream().findFirst().orElse(new EmbeddingVector(List.of(), props.embeddingDimensions()));
    }

    @Override
    public List<EmbeddingVector> embedAll(List<String> texts) {
        if (texts == null || texts.isEmpty()) return List.of();
        try {
            String url = props.baseUrl() + EMBEDDINGS_URL;
            Map<String, Object> body = new HashMap<>();
            body.put("model", props.embeddingModel());
            body.put("input", texts);

            String jsonBody = objectMapper.writeValueAsString(body);
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + props.apiKey())
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .timeout(Duration.ofSeconds(120)).build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.error("DashScope embedding failed: {} {}", response.statusCode(), response.body());
                throw new RuntimeException("嵌入 API 调用失败: " + response.statusCode());
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode data = root.get("data");
            List<EmbeddingVector> results = new ArrayList<>();
            if (data != null && data.isArray()) {
                for (JsonNode item : data) {
                    JsonNode embedding = item.get("embedding");
                    List<Float> vector = new ArrayList<>();
                    if (embedding != null && embedding.isArray()) {
                        for (JsonNode v : embedding) {
                            vector.add((float) v.asDouble());
                        }
                    }
                    results.add(new EmbeddingVector(vector, props.embeddingDimensions()));
                }
            }
            return results;
        } catch (Exception e) {
            log.error("embedAll 调用失败", e);
            throw new RuntimeException("DashScope 嵌入异常: " + e.getMessage(), e);
        }
    }
}
