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
public class HttpDashScopeRerankClient implements DashScopeRerankClient {
    private static final Logger log = LoggerFactory.getLogger(HttpDashScopeRerankClient.class);
    private static final String RERANK_URL = "/services/rerank/rerank";

    private final DashScopeProperties props;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public HttpDashScopeRerankClient(DashScopeProperties props, ObjectMapper objectMapper) {
        this.props = props;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    @Override
    public List<RerankHit> rerank(String query, List<String> documents, int topN) {
        try {
            String url = props.rerankBaseUrl() + RERANK_URL;
            Map<String, Object> body = new HashMap<>();
            body.put("model", props.rerankModel());
            body.put("query", query);
            body.put("documents", documents);
            body.put("top_n", topN);
            body.put("return_documents", false);

            String jsonBody = objectMapper.writeValueAsString(body);
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + props.apiKey())
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .timeout(Duration.ofSeconds(60)).build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.error("DashScope rerank failed: {} {}", response.statusCode(), response.body());
                throw new RuntimeException("重排 API 调用失败: " + response.statusCode());
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode results = root.get("results");
            List<RerankHit> hits = new ArrayList<>();
            if (results != null && results.isArray()) {
                for (JsonNode item : results) {
                    int index = item.get("index").asInt();
                    double score = item.get("relevance_score").asDouble();
                    String text = documents.get(index);
                    hits.add(new RerankHit(index, score, text));
                }
            }
            return hits;
        } catch (Exception e) {
            log.error("rerank 调用失败", e);
            throw new RuntimeException("DashScope 重排异常: " + e.getMessage(), e);
        }
    }
}
