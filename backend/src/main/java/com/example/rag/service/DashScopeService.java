package com.example.rag.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.util.*;

@Service
public class DashScopeService {
    private final RestClient client;
    private final ObjectMapper mapper = new ObjectMapper();
    private final String key;
    private final String embeddingModel;
    private final String chatModel;
    private final String rerankModel;
    private final RestClient rerankClient;

    public DashScopeService(@Value("${dashscope.base-url}") String base,
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
    }

    private void requireKey() {
        if (key == null || key.isBlank())
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "DASHSCOPE_API_KEY 未配置");
    }

    public List<Float> embed(String text) {
        requireKey();
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("无法为不含文本内容的文档片段创建向量");
        }
        // The configured endpoint is DashScope's OpenAI-compatible API. It expects
        // a string or an array of strings instead of DashScope's native input.texts shape.
        Map<String, Object> body = Map.of("model", embeddingModel, "input", List.of(text), "dimensions", 1024, "encoding_format", "float");
        JsonNode root = client.post().uri("/embeddings").header("Authorization", "Bearer " + key).contentType(MediaType.APPLICATION_JSON).body(body).retrieve().body(JsonNode.class);
        JsonNode vector = root.path("output").path("embeddings").path(0).path("embedding");
        if (!vector.isArray()) vector = root.path("data").path(0).path("embedding");
        List<Float> result = new ArrayList<>();
        vector.forEach(n -> result.add(n.floatValue()));
        return result;
    }

    /** Query expansion is best effort; the original query remains the fallback. */
    public List<String> rewriteQueries(String question, int maximum) {
        if (question == null || question.isBlank() || maximum <= 0 || key == null || key.isBlank()) return List.of();
        try {
            String instruction = "Return only a JSON array of at most " + maximum
                    + " concise search rewrites. Preserve identifiers, product names, dates and intent. Do not answer. Query: " + question;
            Map<String, Object> body = Map.of("model", chatModel, "messages", List.of(
                    Map.of("role", "system", "content", "You generate enterprise search queries."),
                    Map.of("role", "user", "content", instruction)), "temperature", 0);
            JsonNode root = client.post().uri("/chat/completions").header("Authorization", "Bearer " + key)
                    .contentType(MediaType.APPLICATION_JSON).body(body).retrieve().body(JsonNode.class);
            String content = root.path("choices").path(0).path("message").path("content").asText("").trim();
            if (content.startsWith("```")) {
                int firstNewline = content.indexOf('\n');
                int lastFence = content.lastIndexOf("```");
                if (firstNewline >= 0 && lastFence > firstNewline) content = content.substring(firstNewline + 1, lastFence).trim();
            }
            JsonNode values = mapper.readTree(content);
            if (!values.isArray()) return List.of();
            List<String> result = new ArrayList<>();
            for (JsonNode value : values) {
                String rewritten = value.asText("").trim();
                if (!rewritten.isBlank() && rewritten.length() <= 300 && !result.contains(rewritten)) result.add(rewritten);
                if (result.size() >= maximum) break;
            }
            return result;
        } catch (Exception ignored) {
            return List.of();
        }
    }

    public record RerankResult(int index, double score) {
    }

    public List<RerankResult> rerank(String query, List<String> documents, int maximum) {
        requireKey();
        if (query == null || query.isBlank() || documents == null || documents.isEmpty()) return List.of();
        int topN = Math.max(1, Math.min(maximum, documents.size()));
        Map<String, Object> body = Map.of("model", rerankModel,
                "input", Map.of("query", query, "documents", documents),
                "parameters", Map.of("return_documents", false, "top_n", topN));
        JsonNode results = rerankClient.post().uri("/api/v1/services/rerank/text-rerank")
                .header("Authorization", "Bearer " + key).contentType(MediaType.APPLICATION_JSON)
                .body(body).retrieve().body(JsonNode.class).path("output").path("results");
        if (!results.isArray()) return List.of();
        List<RerankResult> ranked = new ArrayList<>();
        for (JsonNode result : results) {
            int index = result.path("index").asInt(-1);
            double score = result.path("relevance_score").asDouble(Double.NaN);
            if (index >= 0 && index < documents.size() && Double.isFinite(score)) ranked.add(new RerankResult(index, score));
        }
        return ranked;
    }

    public String chat(String question, String context) {
        requireKey();
        String prompt = "仅依据以下企业知识库上下文回答问题。每条已标记【来源:文档ID-页码】的上下文，"
                + "如被用于回答事实，必须在对应句末原样标注该来源；不得标注未提供的来源，也不要输出通用的[引用]。"
                + "如果上下文不足，请明确说明未找到相关信息。\n上下文：\n" + context + "\n问题：" + question;
        Map<String, Object> body = Map.of("model", chatModel, "messages", List.of(Map.of("role", "system", "content", "你是企业知识助手，禁止编造信息。"), Map.of("role", "user", "content", prompt)), "temperature", 0.2);
        JsonNode root = client.post().uri("/chat/completions").header("Authorization", "Bearer " + key).contentType(MediaType.APPLICATION_JSON).body(body).retrieve().body(JsonNode.class);
        return root.path("choices").path(0).path("message").path("content").asText("未找到相关答案");
    }
}
