package com.example.rag.framework.client;

import java.util.List;
import java.util.Map;

/**
 * DashScope 聊天补全 API 抽象。同时支持阻塞调用 {@link #complete(ChatRequest)} 与流式调用
 * {@link #stream(ChatRequest, StreamHandler)}。rag-biz 业务层应只依赖本接口，不应自行构造 HTTP 请求。
 */
public interface DashScopeChatClient {

    /**
     * Run a non-streaming chat completion. The returned assistant text is
     * guaranteed to be non-null; callers can inspect {@link ChatResponse#usage()}
     * for token accounting.
     */
    ChatResponse complete(ChatRequest request);

    /**
     * 流式聊天补全。回调按 delta 逐段触发，最终以 type="done" 收尾。
     * 不可恢复的错误会以 {@link RuntimeException} 抛出，调用方应捕获并转换为业务错误事件。
     */
    void stream(ChatRequest request, StreamHandler handler);

    /** 单条对话消息（system / user / assistant）。 */
    record ChatMessage(String role, String content) {
    }

    /** Chat completion request. */
    record ChatRequest(List<ChatMessage> messages, Double temperature, Integer maxTokens,
                        Map<String, Object> extra) {
        public ChatRequest {
            messages = messages == null ? List.of() : List.copyOf(messages);
            extra = extra == null ? Map.of() : Map.copyOf(extra);
        }
    }

    /** Chat completion response. */
    record ChatResponse(String content, Integer promptTokens, Integer completionTokens,
                        Integer totalTokens, String finishReason) {
        public ChatResponse {
            if (content == null) content = "";
        }
    }

    /** 流式事件载荷。type ∈ {"delta","done","error"}。 */
    record StreamEvent(String type, String delta, String content, String finishReason) {
        public static StreamEvent delta(String delta) {
            return new StreamEvent("delta", delta, null, null);
        }

        public static StreamEvent done(String content, String finishReason) {
            return new StreamEvent("done", null, content, finishReason);
        }

        public static StreamEvent error(String message) {
            return new StreamEvent("error", null, message, null);
        }
    }

    /** Streaming callback. */
    @FunctionalInterface
    interface StreamHandler {
        void onEvent(StreamEvent event);
    }
}
