package com.aoneng.rag.infra.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * DashScope LLM 服务实现。
 * DashScope OpenAI 兼容接口的 REST 封装。
 */
@Component
public class DashScopeLlmService implements LlmService {

    private final RestClient client;
    private final RestClient rerankClient;
    private final WebClient webClient;
    private final ObjectMapper mapper = new ObjectMapper();
    private final String key;
    private final String embeddingModel;
    private final String chatModel;
    private final String rerankModel;

    public DashScopeLlmService(@Value("${dashscope.base-url}") String base,
                                @Value("${dashscope.rerank-base-url:https://dashscope.aliyuncs.com}") String rerankBaseUrl,
                                @Value("${dashscope.api-key:}") String key,
                                @Value("${dashscope.embedding-model:text-embedding-v4}") String embeddingModel,
                                @Value("${dashscope.chat-model:qwen-plus}") String chatModel,
                                @Value("${dashscope.rerank-model:qwen3-rerank}") String rerankModel) {
        this.key = key;
        this.embeddingModel = embeddingModel;
        this.chatModel = chatModel;
        this.rerankModel = rerankModel;
        this.client = RestClient.builder().baseUrl(base).build();
        this.rerankClient = RestClient.builder().baseUrl(rerankBaseUrl).build();
        this.webClient = WebClient.builder().baseUrl(base).defaultHeader("Authorization", "Bearer " + key).build();
    }

    private void requireKey() {
        if (key == null || key.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "DASHSCOPE_API_KEY 未配置");
        }
    }

    /** Avoid Spring 7 tools.jackson conversion of com.fasterxml JsonNode responses. */
    private JsonNode postJson(RestClient target, String uri, Object body) {
        byte[] bytes = target.post().uri(uri)
                .header("Authorization", "Bearer " + key)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body).retrieve().body(byte[].class);
        if (bytes == null || bytes.length == 0) throw new IllegalStateException("模型服务返回空响应");
        try {
            return mapper.readTree(new String(bytes, StandardCharsets.UTF_8));
        } catch (Exception failure) {
            throw new IllegalStateException("模型服务返回无效 JSON", failure);
        }
    }

    @Override
    public List<Float> embed(String text) {
        requireKey();
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("无法为不含文本内容的文档片段创建向量");
        }
        java.util.Map<String, Object> body = java.util.Map.of(
                "model", embeddingModel,
                "input", List.of(text),
                "dimensions", 1024,
                "encoding_format", "float");
        JsonNode root = postJson(client, "/embeddings", body);
        JsonNode vector = root.path("output").path("embeddings").path(0).path("embedding");
        if (!vector.isArray()) vector = root.path("data").path(0).path("embedding");
        List<Float> result = new ArrayList<>();
        vector.forEach(n -> result.add(n.floatValue()));
        return result;
    }

    @Override
    public List<String> rewriteQueries(String question, int maximum) {
        if (question == null || question.isBlank() || maximum <= 0 || key == null || key.isBlank()) {
            return List.of();
        }
        try {
            String instruction = "Return only a JSON array of at most " + maximum
                    + " concise search rewrites. Preserve identifiers, product names, dates and intent. Do not answer. Query: " + question;
            java.util.Map<String, Object> body = java.util.Map.of("model", chatModel,
                    "messages", List.of(
                            java.util.Map.of("role", "system", "content", "You generate enterprise search queries."),
                            java.util.Map.of("role", "user", "content", instruction)),
                    "temperature", 0);
            JsonNode root = postJson(client, "/chat/completions", body);
            String content = root.path("choices").path(0).path("message").path("content").asText("").trim();
            if (content.startsWith("```")) {
                int firstNewline = content.indexOf('\n');
                int lastFence = content.lastIndexOf("```");
                if (firstNewline >= 0 && lastFence > firstNewline) {
                    content = content.substring(firstNewline + 1, lastFence).trim();
                }
            }
            JsonNode values = mapper.readTree(content);
            if (!values.isArray()) return List.of();
            List<String> result = new ArrayList<>();
            for (JsonNode value : values) {
                String rewritten = value.asText("").trim();
                if (!rewritten.isBlank() && rewritten.length() <= 300 && !result.contains(rewritten)) {
                    result.add(rewritten);
                }
                if (result.size() >= maximum) break;
            }
            return result;
        } catch (Exception ignored) {
            return List.of();
        }
    }

    @Override
    public List<RerankResult> rerank(String query, List<String> documents, int maximum) {
        requireKey();
        if (query == null || query.isBlank() || documents == null || documents.isEmpty()) {
            return List.of();
        }
        int topN = Math.max(1, Math.min(maximum, documents.size()));
        java.util.Map<String, Object> body = java.util.Map.of("model", rerankModel,
                "input", java.util.Map.of("query", query, "documents", documents),
                "parameters", java.util.Map.of("return_documents", false, "top_n", topN));
        JsonNode results = postJson(rerankClient, "/api/v1/services/rerank/text-rerank", body)
                .path("output").path("results");
        if (!results.isArray()) return List.of();
        List<RerankResult> ranked = new ArrayList<>();
        for (JsonNode result : results) {
            int index = result.path("index").asInt(-1);
            double score = result.path("relevance_score").asDouble(Double.NaN);
            if (index >= 0 && index < documents.size() && Double.isFinite(score)) {
                ranked.add(new RerankResult(index, score));
            }
        }
        return ranked;
    }

    @Override
    public String chat(String question, String context) {
        requireKey();
        String prompt = "仅依据以下企业知识库上下文回答问题。每条已标记【来源:文档ID-页码】的上下文，"
                + "如被用于回答事实，必须在对应句末原样标注该来源；不得标注未提供的来源，也不要输出通用的[引用]。"
                + "如果上下文不足，请明确说明未找到相关信息。\n上下文：\n" + context + "\n问题：" + question;
        java.util.Map<String, Object> body = java.util.Map.of("model", chatModel,
                "messages", List.of(
                        java.util.Map.of("role", "system", "content", "你是企业知识助手，禁止编造信息。"),
                        java.util.Map.of("role", "user", "content", prompt)),
                "temperature", 0.2);
        JsonNode root = postJson(client, "/chat/completions", body);
        return root.path("choices").path(0).path("message").path("content").asText("未找到相关答案");
    }

    @Override
    public void streamChat(String question, String context, Consumer<String> onDelta,
                           BooleanSupplier cancelled) {
        requireKey();
        Objects.requireNonNull(onDelta, "onDelta");
        String prompt = "仅依据以下企业知识库上下文回答问题。每条已标记【来源:文档ID-页码】的上下文，"
                + "如被用于回答事实，必须在对应句末原样标注该来源；不得标注未提供的来源，也不要输出通用的[引用]。"
                + "如果上下文不足，请明确说明未找到相关信息。\n上下文：\n" + (context == null ? "" : context)
                + "\n问题：" + (question == null ? "" : question);
        java.util.Map<String, Object> body = java.util.Map.of(
                "model", chatModel,
                "messages", List.of(
                        java.util.Map.of("role", "system", "content", "你是企业知识助手，禁止编造信息。"),
                        java.util.Map.of("role", "user", "content", prompt)),
                "temperature", 0.2,
                "stream", true
        );
        client.post().uri("/chat/completions")
                .header("Authorization", "Bearer " + key)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange((request, response) -> {
                    if (response.getStatusCode().isError()) {
                        throw new IllegalStateException("模型服务暂时不可用（HTTP " + response.getStatusCode().value() + "）");
                    }
                    try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(response.getBody(), StandardCharsets.UTF_8))) {
                        String line;
                        StringBuilder data = new StringBuilder();
                        while ((line = reader.readLine()) != null) {
                            if (cancelled.getAsBoolean()) break;
                            if (line.isEmpty()) {
                                if (dispatchStreamData(data, onDelta)) break;
                                data.setLength(0);
                            } else if (line.startsWith("data:")) {
                                if (data.length() > 0) data.append('\n');
                                data.append(line.substring(5).trim());
                            }
                        }
                        if (!data.isEmpty() && !cancelled.getAsBoolean()) {
                            dispatchStreamData(data, onDelta);
                        }
                    } catch (IOException e) {
                        if (!cancelled.getAsBoolean()) throw new IllegalStateException("模型流读取失败", e);
                    }
                    return null;
                });
    }

    @Override
    public Flux<String> streamChatFlux(String question, String context) {
        requireKey();
        String prompt = "仅依据以下企业知识库上下文回答问题。每条已标记【来源:文档ID-页码】的上下文，"
                + "如被用于回答事实，必须在对应句末原样标注该来源；不得标注未提供的来源，也不要输出通用的[引用]。"
                + "如果上下文不足，请明确说明未找到相关信息。\n上下文：\n" + (context == null ? "" : context)
                + "\n问题：" + (question == null ? "" : question);
        java.util.Map<String, Object> body = java.util.Map.of("model", chatModel,
                "messages", List.of(
                        java.util.Map.of("role", "system", "content", "你是企业知识助手，禁止编造信息。"),
                        java.util.Map.of("role", "user", "content", prompt)),
                "temperature", 0.2, "stream", true);
        return webClient.post().uri("/chat/completions").contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM).bodyValue(body).retrieve()
                .onStatus(status -> status.isError(), response -> Mono.error(
                        new IllegalStateException("模型服务暂时不可用（HTTP " + response.statusCode().value() + "）")))
                .bodyToFlux(new org.springframework.core.ParameterizedTypeReference<ServerSentEvent<String>>() {})
                .map(ServerSentEvent::data).filter(Objects::nonNull)
                .takeUntil("[DONE]"::equals)
                .handle((data, sink) -> {
                    if ("[DONE]".equals(data)) return;
                    try {
                        JsonNode root = mapper.readTree(data);
                        JsonNode error = root.path("error");
                        if (error.isObject()) throw new IllegalStateException("模型服务返回错误");
                        JsonNode delta = root.path("choices").path(0).path("delta").path("content");
                        if (delta.isTextual() && !delta.textValue().isEmpty()) sink.next(delta.textValue());
                    } catch (IllegalStateException e) {
                        sink.error(e);
                    } catch (Exception ignored) {
                        // Ignore provider metadata events.
                    }
                });
    }

    private boolean dispatchStreamData(StringBuilder data, Consumer<String> onDelta) {
        if (data.isEmpty()) return false;
        String value = data.toString().trim();
        if ("[DONE]".equals(value)) return true;
        try {
            JsonNode root = mapper.readTree(value);
            JsonNode error = root.path("error");
            if (error.isObject()) {
                String message = error.path("message").asText("").trim();
                throw new IllegalStateException("模型服务返回错误" + (message.isBlank() ? "" : ": " + message));
            }
            JsonNode delta = root.path("choices").path(0).path("delta").path("content");
            if (delta.isMissingNode()) {
                delta = root.path("output").path("choices").path(0).path("delta").path("content");
            }
            if (delta.isTextual() && !delta.textValue().isEmpty()) onDelta.accept(delta.textValue());
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception ignored) {
            // Provider keep-alive and metadata events do not contain answer text.
        }
        return false;
    }
}
