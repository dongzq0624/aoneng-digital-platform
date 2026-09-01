package com.example.rag.service.impl;

import com.example.rag.service.DashScopeService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.http.HttpStatus;
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
 * 通义千问服务默认实现。
 * DashScope OpenAI 兼容接口的 REST 封装。构造两个 {@link RestClient}
 * （chat/embedding 与 rerank，因为它们可能位于不同的 base URL），
 * 并使用 {@link WebClient} 处理响应式 SSE 流水线。
 */
@Service
public class DashScopeServiceImpl implements DashScopeService {

    private final RestClient client;
    private final ObjectMapper mapper = new ObjectMapper();
    private final String key;
    private final String embeddingModel;
    private final String chatModel;
    private final String rerankModel;
    private final RestClient rerankClient;
    private final WebClient webClient;

    public DashScopeServiceImpl(@Value("${dashscope.base-url}") String base,
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

    /**
     * 检查 API Key 是否配置。未配置时抛出 503。
     */
    private void requireKey() {
        if (key == null || key.isBlank())
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "DASHSCOPE_API_KEY 未配置");
    }

    /**
     * 将文本嵌入为 1024 维向量。
     *
     * @param text 待嵌入的文本
     * @return 嵌入向量
     * @throws IllegalArgumentException 输入为空时抛出
     * @throws ResponseStatusException API Key 未配置时抛出
     */
    @Override
    public List<Float> embed(String text) {
        requireKey();
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("无法为不含文本内容的文档片段创建向量");
        }
        // DashScope 的 OpenAI 兼容接口接受字符串或字符串数组，而不是原生的 input.texts 格式。
        java.util.Map<String, Object> body = java.util.Map.of(
                "model", embeddingModel,
                "input", List.of(text),
                "dimensions", 1024,
                "encoding_format", "float");
        JsonNode root = client.post().uri("/embeddings")
                .header("Authorization", "Bearer " + key)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(JsonNode.class);
        JsonNode vector = root.path("output").path("embeddings").path(0).path("embedding");
        if (!vector.isArray()) vector = root.path("data").path(0).path("embedding");
        List<Float> result = new ArrayList<>();
        vector.forEach(n -> result.add(n.floatValue()));
        return result;
    }

    /**
     * 生成查询改写。尽力而为；失败时返回空列表。
     *
     * @param question 原始问题
     * @param maximum 最大改写数量
     * @return 改写后的查询列表
     */
    @Override
    public List<String> rewriteQueries(String question, int maximum) {
        if (question == null || question.isBlank() || maximum <= 0 || key == null || key.isBlank()) return List.of();
        try {
            String instruction = "Return only a JSON array of at most " + maximum
                    + " concise search rewrites. Preserve identifiers, product names, dates and intent. Do not answer. Query: " + question;
            java.util.Map<String, Object> body = java.util.Map.of("model", chatModel,
                    "messages", List.of(
                            java.util.Map.of("role", "system", "content", "You generate enterprise search queries."),
                            java.util.Map.of("role", "user", "content", instruction)),
                    "temperature", 0);
            JsonNode root = client.post().uri("/chat/completions")
                    .header("Authorization", "Bearer " + key)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
            String content = root.path("choices").path(0).path("message").path("content").asText("").trim();
            if (content.startsWith("```")) {
                int firstNewline = content.indexOf('\n');
                int lastFence = content.lastIndexOf("```");
                if (firstNewline >= 0 && lastFence > firstNewline)
                    content = content.substring(firstNewline + 1, lastFence).trim();
            }
            JsonNode values = mapper.readTree(content);
            if (!values.isArray()) return List.of();
            List<String> result = new ArrayList<>();
            for (JsonNode value : values) {
                String rewritten = value.asText("").trim();
                if (!rewritten.isBlank() && rewritten.length() <= 300 && !result.contains(rewritten))
                    result.add(rewritten);
                if (result.size() >= maximum) break;
            }
            return result;
        } catch (Exception ignored) {
            return List.of();
        }
    }

    /**
     * 调用 DashScope 重排 API 对文档进行重排。
     *
     * @param query 查询问题
     * @param documents 待重排的文档列表
     * @param maximum 返回的最大结果数
     * @return 重排结果列表
     */
    @Override
    public List<RerankResult> rerank(String query, List<String> documents, int maximum) {
        requireKey();
        if (query == null || query.isBlank() || documents == null || documents.isEmpty()) return List.of();
        int topN = Math.max(1, Math.min(maximum, documents.size()));
        java.util.Map<String, Object> body = java.util.Map.of("model", rerankModel,
                "input", java.util.Map.of("query", query, "documents", documents),
                "parameters", java.util.Map.of("return_documents", false, "top_n", topN));
        JsonNode results = rerankClient.post().uri("/api/v1/services/rerank/text-rerank")
                .header("Authorization", "Bearer " + key)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(JsonNode.class)
                .path("output").path("results");
        if (!results.isArray()) return List.of();
        List<RerankResult> ranked = new ArrayList<>();
        for (JsonNode result : results) {
            int index = result.path("index").asInt(-1);
            double score = result.path("relevance_score").asDouble(Double.NaN);
            if (index >= 0 && index < documents.size() && Double.isFinite(score))
                ranked.add(new RerankResult(index, score));
        }
        return ranked;
    }

    /**
     * 阻塞式聊天补全。返回模型作为单个字符串的回答。
     *
     * @param question 用户问题
     * @param context  上下文（包含引用文档）
     * @return 模型回答
     */
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
        JsonNode root = client.post().uri("/chat/completions")
                .header("Authorization", "Bearer " + key)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(JsonNode.class);
        return root.path("choices").path(0).path("message").path("content").asText("未找到相关答案");
    }

    /**
     * 通过回调函数流式返回模型回答。
     *
     * @param question 用户问题
     * @param context  上下文
     * @param onDelta  每个非空增量文本的回调
     * @param cancelled 取消检查
     */
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
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.getBody(), StandardCharsets.UTF_8))) {
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
                        if (!data.isEmpty() && !cancelled.getAsBoolean()) dispatchStreamData(data, onDelta);
                    } catch (IOException e) {
                        if (!cancelled.getAsBoolean()) throw new IllegalStateException("模型流读取失败", e);
                    }
                    return null;
                });
    }

    /**
     * 响应式流式聊天变体，供 WebFlux 聊天流水线使用。
     *
     * @param question 用户问题
     * @param context  上下文
     * @return 文本增量流
     */
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
            if (delta.isMissingNode())
                delta = root.path("output").path("choices").path(0).path("delta").path("content");
            if (delta.isTextual() && !delta.textValue().isEmpty()) onDelta.accept(delta.textValue());
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception ignored) {
            // Provider keep-alive and metadata events do not contain answer text.
        }
        return false;
    }
}
