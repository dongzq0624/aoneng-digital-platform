package com.aoneng.rag.infra.client;

import com.aoneng.rag.infra.config.DashScopeProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class HttpDashScopeChatClient implements DashScopeChatClient {
    private static final Logger log = LoggerFactory.getLogger(HttpDashScopeChatClient.class);
    private static final String CHAT_COMPLETIONS = "/chat/completions";
    private final DashScopeProperties props;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public HttpDashScopeChatClient(DashScopeProperties props, ObjectMapper objectMapper) {
        this.props = props;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    @Override
    public ChatResponse complete(ChatRequest request) {
        try {
            String url = props.baseUrl() + CHAT_COMPLETIONS;
            Map<String, Object> body = buildRequestBody(request, false);
            String jsonBody = objectMapper.writeValueAsString(body);
            HttpRequest httpReq = HttpRequest.newBuilder().uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + props.apiKey())
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .timeout(Duration.ofSeconds(120)).build();
            HttpResponse<String> resp = httpClient.send(httpReq, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                log.error("DashScope API error: {} {}", resp.statusCode(), resp.body());
                throw new RuntimeException("DashScope API 调用失败: " + resp.statusCode());
            }
            var node = objectMapper.readTree(resp.body());
            String content = node.at("/choices/0/message/content").asText();
            int promptTokens = node.at("/usage/prompt_tokens").asInt();
            int completionTokens = node.at("/usage/completion_tokens").asInt();
            String finishReason = node.at("/choices/0/finish_reason").asText();
            return new ChatResponse(content, promptTokens, completionTokens, promptTokens + completionTokens, finishReason);
        } catch (Exception e) {
            log.error("complete 调用失败", e);
            throw new RuntimeException("DashScope 对话调用异常: " + e.getMessage(), e);
        }
    }

    @Override
    public void stream(ChatRequest request, StreamHandler handler) {
        try {
            String url = props.baseUrl() + CHAT_COMPLETIONS;
            Map<String, Object> body = buildRequestBody(request, true);
            String jsonBody = objectMapper.writeValueAsString(body);
            HttpRequest httpReq = HttpRequest.newBuilder().uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + props.apiKey())
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .timeout(Duration.ofSeconds(120)).build();
            HttpResponse<java.io.InputStream> resp = httpClient.send(httpReq, HttpResponse.BodyHandlers.ofInputStream());
            if (resp.statusCode() != 200) {
                handler.onEvent(StreamEvent.error("流式调用失败: " + resp.statusCode()));
                return;
            }
            StringBuilder contentBuilder = new StringBuilder();
            String finishReason = null;
            try (var reader = new java.io.BufferedReader(new java.io.InputStreamReader(resp.body()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("data: ")) {
                        String data = line.substring(6).trim();
                        if (data.equals("[DONE]")) break;
                        try {
                            var node = objectMapper.readTree(data);
                            String delta = node.at("/choices/0/delta/content").asText("");
                            if (!delta.isEmpty()) {
                                contentBuilder.append(delta);
                                handler.onEvent(StreamEvent.delta(delta));
                            }
                            if (!node.at("/choices/0/finish_reason").isNull()) {
                                finishReason = node.at("/choices/0/finish_reason").asText();
                            }
                        } catch (Exception e) { log.warn("解析 SSE 行失败: {}", data); }
                    }
                }
            }
            handler.onEvent(StreamEvent.done(contentBuilder.toString(), finishReason));
        } catch (Exception e) {
            log.error("stream 调用失败", e);
            handler.onEvent(StreamEvent.error("流式调用异常: " + e.getMessage()));
        }
    }

    private Map<String, Object> buildRequestBody(ChatRequest request, boolean stream) {
        Map<String, Object> body = new HashMap<>();
        body.put("model", props.chatModel());
        body.put("stream", stream);
        List<Map<String, String>> messages = new ArrayList<>();
        for (ChatMessage msg : request.messages()) {
            messages.add(Map.of("role", msg.role(), "content", msg.content()));
        }
        body.put("messages", messages);
        if (request.temperature() != null) body.put("temperature", request.temperature());
        if (request.maxTokens() != null) body.put("max_tokens", request.maxTokens());
        request.extra().forEach(body::put);
        return body;
    }
}
