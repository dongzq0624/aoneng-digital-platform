package com.example.rag.infra.client;

import java.util.List;
import java.util.Map;

public interface DashScopeChatClient {
    ChatResponse complete(ChatRequest request);
    void stream(ChatRequest request, StreamHandler handler);

    record ChatMessage(String role, String content) {}
    record ChatRequest(List<ChatMessage> messages, Double temperature, Integer maxTokens, Map<String, Object> extra) {
        public ChatRequest {
            messages = messages == null ? List.of() : List.copyOf(messages);
            extra = extra == null ? Map.of() : Map.copyOf(extra);
        }
    }
    record ChatResponse(String content, Integer promptTokens, Integer completionTokens, Integer totalTokens, String finishReason) {
        public ChatResponse {
            if (content == null) content = "";
        }
    }
    record StreamEvent(String type, String delta, String content, String finishReason) {
        public static StreamEvent delta(String delta) { return new StreamEvent("delta", delta, null, null); }
        public static StreamEvent done(String content, String finishReason) { return new StreamEvent("done", null, content, finishReason); }
        public static StreamEvent error(String message) { return new StreamEvent("error", null, message, null); }
    }
    @FunctionalInterface
    interface StreamHandler { void onEvent(StreamEvent event); }
}
