package com.example.rag.chat.vo;

import java.time.Instant;
import java.util.List;

/**
 * 会话响应体。
 */
public record ConversationVO(
        long id,
        long userId,
        String title,
        List<Long> selectedKbIds,
        Instant lastMessageAt,
        Instant createdAt,
        Instant updatedAt) {
}
