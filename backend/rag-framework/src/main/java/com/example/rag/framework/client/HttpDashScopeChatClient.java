package com.example.rag.framework.client;

import com.example.rag.framework.config.DashScopeProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * DashScope chat client 的默认实现，基于 DashScope 的 OpenAI 兼容 <code>/chat/completions</code> 端点，
 * 同时支持非流式与流式（SSE）两种调用方式。使用 JDK 自带 {@link HttpClient}，避免框架模块引入额外 HTTP 依赖。
 * 业务层只依赖 {@link DashScopeChatClient} 接口，不应直接调用此实现。
 */
@Component
public class HttpDashScopeChatClient implements DashScopeChatClient {
    private static final Logger log = LoggerFactory.getLogger(HttpDashScopeChatClient.class);

    private final DashScopeProperties properties;
    private final HttpClient http;
    private final ObjectMapper mapper;

    /**
     * 构造默认聊天客户端。
     *
     * @param properties DashScope 配置（API Key、Base URL、模型名）
     * @param mapper     复用的 Jackson 序列化器
     */
    public HttpDashScopeChatClient(DashScopeProperties properties, ObjectMapper mapper) {
        this.properties = properties;
        this.mapper = mapper;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public ChatResponse complete(ChatRequest request) {
        // 非流式调用：单次请求 / 响应。出错时抛 IllegalStateException，由调用方决定如何降级。
        try {
            ObjectNode body = buildBody(request, false);
            HttpResponse<String> response = send(properties.baseUrl() + "/chat/completions", body.toString());
            JsonNode json = mapper.readTree(response.body());
            JsonNode choice = json.path("choices").path(0);
            String content = choice.path("message").path("content").asText("");
            JsonNode usage = json.path("usage");
            return new ChatResponse(
                    content,
                    emptyAsNull(usage.path("prompt_tokens")),
                    emptyAsNull(usage.path("completion_tokens")),
                    emptyAsNull(usage.path("total_tokens")),
                    choice.path("finish_reason").asText(null)
            );
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new IllegalStateException("调用 DashScope chat 接口失败：" + e.getMessage(), e);
        }
    }

    @Override
    public void stream(ChatRequest request, StreamHandler handler) {
        // 流式调用：解析 SSE 事件，逐段回调 handler，最后以 done 事件收尾。
        // 非 2xx 响应或网络异常会被转换为错误事件 + RuntimeException。
        try {
            ObjectNode body = buildBody(request, true);
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(properties.baseUrl() + "/chat/completions"))
                    .timeout(Duration.ofSeconds(120))
                    .header("Authorization", "Bearer " + properties.apiKey())
                    .header("Content-Type", "application/json")
                    .header("Accept", "text/event-stream")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<InputStream> response = http.send(httpRequest, HttpResponse.BodyHandlers.ofInputStream());
            int status = response.statusCode();
            if (status / 100 != 2) {
                String preview;
                try (InputStream is = response.body()) {
                    preview = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                }
                log.debug("DashScope stream HTTP {} body preview length={}", status, preview.length());
                throw new IllegalStateException("DashScope stream 调用失败，HTTP " + status);
            }
            StringBuilder fullContent = new StringBuilder();
            String finishReason = null;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isBlank()) continue;
                    if (!line.startsWith("data:")) continue;
                    String payload = line.substring(5).trim();
                    if ("[DONE]".equals(payload)) break;
                    JsonNode json;
                    try {
                        json = mapper.readTree(payload);
                    } catch (IOException parseFailure) {
                        log.debug("DashScope stream non-JSON line skipped: {}", parseFailure.getMessage());
                        continue;
                    }
                    JsonNode choice = json.path("choices").path(0);
                    if (choice.isMissingNode()) continue;
                    String delta = choice.path("delta").path("content").asText("");
                    String reason = choice.path("finish_reason").asText(null);
                    if (!delta.isEmpty()) {
                        fullContent.append(delta);
                        handler.onEvent(DashScopeChatClient.StreamEvent.delta(delta));
                    }
                    if (reason != null && !reason.isBlank()) {
                        finishReason = reason;
                    }
                }
            }
            handler.onEvent(DashScopeChatClient.StreamEvent.done(fullContent.toString(), finishReason));
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            handler.onEvent(DashScopeChatClient.StreamEvent.error("dashscope stream failed: " + e.getMessage()));
            throw new IllegalStateException("调用 DashScope chat 接口失败：" + e.getMessage(), e);
        }
    }

    /**
     * 构造请求体 JSON。{@code stream=true} 时 DashScope 返回 SSE，{@code stream=false} 返回标准 JSON。
     *
     * @param request 业务请求参数
     * @param stream  是否启用流式
     * @return OpenAPI 兼容的请求体
     */
    private ObjectNode buildBody(ChatRequest request, boolean stream) {
        ObjectNode body = mapper.createObjectNode();
        body.put("model", properties.chatModel());
        body.put("stream", stream);
        if (request.temperature() != null) body.put("temperature", request.temperature());
        if (request.maxTokens() != null && request.maxTokens() > 0) body.put("max_tokens", request.maxTokens());
        ArrayNode messages = body.putArray("messages");
        for (ChatMessage message : request.messages()) {
            ObjectNode node = messages.addObject();
            node.put("role", message.role());
            node.put("content", message.content() == null ? "" : message.content());
        }
        return body;
    }

    /**
     * 发送同步请求并校验 2xx 响应。非 2xx 时抛 {@link IllegalStateException}，不向上游打印完整响应体以避免泄露提示词。
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
            throw new IllegalStateException("DashScope chat 调用失败，HTTP " + response.statusCode());
        }
        return response;
    }

    private static Integer emptyAsNull(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) return null;
        return node.asInt();
    }

    /**
     * Compile-time sanity helper so the {@link ArrayList} import is referenced.
     */
    @SuppressWarnings("unused")
    private static List<String> emptyList() {
        return new ArrayList<>();
    }
}
