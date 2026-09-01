package com.example.rag.framework.client;

import com.example.rag.framework.config.DashScopeProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Embedding 客户端默认实现，基于 DashScope OpenAI 兼容的 <code>/embeddings</code> 端点。
 * 使用 JDK 自带 {@link HttpClient}，框架模块不引入额外 HTTP 依赖。
 */
@Component
public class HttpDashScopeEmbeddingClient implements DashScopeEmbeddingClient {
    private static final Logger log = LoggerFactory.getLogger(HttpDashScopeEmbeddingClient.class);

    private final DashScopeProperties properties;
    private final HttpClient http;
    private final ObjectMapper mapper;

    /**
     * 构造默认 embedding 客户端。
     */
    public HttpDashScopeEmbeddingClient(DashScopeProperties properties, ObjectMapper mapper) {
        this.properties = properties;
        this.mapper = mapper;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public EmbeddingVector embed(String text) {
        // 单条便捷包装，避免业务层频繁构建 List。
        List<EmbeddingVector> vectors = embedAll(List.of(text));
        if (vectors.isEmpty()) {
            throw new IllegalStateException("DashScope embedding returned no vectors");
        }
        return vectors.get(0);
    }

    @Override
    public List<EmbeddingVector> embedAll(List<String> texts) {
        if (texts == null || texts.isEmpty()) return List.of();
        try {
            ObjectNode body = mapper.createObjectNode();
            body.put("model", properties.embeddingModel());
            body.put("encoding_format", "float");
            ArrayNode input = body.putArray("input");
            for (String text : texts) {
                input.add(text == null ? "" : text);
            }
            HttpResponse<String> response = send(properties.baseUrl() + "/embeddings", body.toString());
            JsonNode json = mapper.readTree(response.body());
            JsonNode data = json.path("data");
            if (!data.isArray()) {
                throw new IllegalStateException("DashScope embedding response missing data array");
            }
            List<EmbeddingVector> result = new ArrayList<>(data.size());
            for (JsonNode node : data) {
                ArrayNode rawVector = (ArrayNode) node.path("embedding");
                List<Float> vec = new ArrayList<>(rawVector.size());
                for (JsonNode v : rawVector) vec.add((float) v.asDouble());
                result.add(new EmbeddingVector(vec, vec.size()));
            }
            if (result.size() != texts.size()) {
                log.warn("DashScope embedding response size mismatch: requested={} returned={}",
                        texts.size(), result.size());
            }
            return result;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new IllegalStateException("调用 DashScope embedding 接口失败：" + e.getMessage(), e);
        }
    }

    /**
     * 发送同步请求并校验 2xx 响应。非 2xx 时抛 {@link IllegalStateException}，不打印上游完整响应体以避免泄露数据。
     */
    private HttpResponse<String> send(String url, String payload) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(60))
                .header("Authorization", "Bearer " + properties.apiKey())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() / 100 != 2) {
            // Avoid leaking the full upstream body in logs; surface only the status.
            throw new IllegalStateException("DashScope embedding 调用失败，HTTP " + response.statusCode());
        }
        return response;
    }
}
