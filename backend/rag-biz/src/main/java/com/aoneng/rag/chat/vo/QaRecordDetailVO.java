package com.aoneng.rag.chat.vo;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 问答记录详情响应体。
 */
public record QaRecordDetailVO(
        long id,
        long userId,
        long conversationId,
        List<Long> kbIds,
        String question,
        String answer,
        List<Long> retrievedChunkIds,
        String modelName,
        Integer latencyMs,
        Integer feedback,
        String rerankScores,
        Map<String, Object> extra,
        Instant createdAt) {
}
