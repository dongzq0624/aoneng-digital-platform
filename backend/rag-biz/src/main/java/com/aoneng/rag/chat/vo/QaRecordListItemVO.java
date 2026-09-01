package com.aoneng.rag.chat.vo;

import java.time.Instant;
import java.util.List;

/**
 * 问答记录列表项响应体。
 */
public record QaRecordListItemVO(
        long id,
        long conversationId,
        List<Long> kbIds,
        String question,
        String answer,
        String modelName,
        Integer latencyMs,
        Integer feedback,
        Instant createdAt) {
}
