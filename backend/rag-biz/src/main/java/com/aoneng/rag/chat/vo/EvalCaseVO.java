package com.aoneng.rag.chat.vo;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 检索评测用例响应体。
 */
public record EvalCaseVO(
        long id,
        String question,
        List<Long> expectedChunkIds,
        String referenceAnswer,
        Boolean enabled,
        String note,
        Map<String, Object> aggregate,
        Instant createdAt,
        Instant updatedAt) {
}
