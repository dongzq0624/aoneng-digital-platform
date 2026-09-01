package com.example.rag.framework.client;

import com.example.rag.framework.config.DashScopeProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Rerank 客户端默认实现。DashScope 的 <code>gte-rerank</code> 系列采用非流式端点，
 * 从 {@code rerankBaseUrl} 与 {@code rerankModel} 配置中读取参数。
 */
@Component
public class HttpDashScopeRerankClient implements DashScopeRerankClient {
    private final DashScopeProperties properties;
    private final HttpClient http;
    private final ObjectMapper mapper;

    /**
     * 构造默认 rerank 客户端。
     */
    public HttpDashScopeRerankClient(DashScopeProperties properties, ObjectMapper mapper) {
        this.properties = properties;
        this.mapper = mapper;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public List<RerankHit> rerank(String query, List<String> documents, int topN) {
        if (documents == null || documents.isEmpty()) return List.of();
        try {
            ObjectNode body = mapper.createObjectNode();
            body.put("model", properties.rerankModel());
            ObjectNode input = body.putObject("input");
            input.put("query", query == null ? "" : query);
            ArrayNode docs = body.putArray("documents");
            for (String document : documents) {
                docs.add(document == null ? "" : document);
            }
            body.put("top_n", Math.max(1, Math.min(topN <= 0 ? documents.size() : topN, documents.size())));
            body.put("return_documents", false);

            String url = stripTrailingSlash(properties.rerankBaseUrl()) + "/api/v1/services/rerank/text-rerank/text-rerank";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Bearer " + properties.apiKey())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() / 100 != 2) {
                throw new IllegalStateException("DashScope rerank 调用失败，HTTP " + response.statusCode());
            }
            JsonNode json = mapper.readTree(response.body());
            JsonNode results = json.path("output").path("results");
            List<RerankHit> hits = new ArrayList<>(results.size());
            for (JsonNode node : results) {
                JsonNode indexNode = node.path("index");
                JsonNode scoreNode = node.path("relevance_score");
                if (indexNode.isMissingNode() || scoreNode.isMissingNode()) continue;
                hits.add(new RerankHit(indexNode.asInt(), scoreNode.asDouble(), null));
            }
            hits.sort(Comparator.comparingDouble(RerankHit::score).reversed());
            return hits;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new IllegalStateException("调用 DashScope rerank 接口失败：" + e.getMessage(), e);
        }
    }

    private static String stripTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
