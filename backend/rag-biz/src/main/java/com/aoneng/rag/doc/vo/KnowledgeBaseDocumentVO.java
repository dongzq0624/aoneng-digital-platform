package com.aoneng.rag.doc.vo;

import java.time.Instant;

/**
 * 知识库文档响应体。
 */
public record KnowledgeBaseDocumentVO(
        long id,
        long kbId,
        String fileName,
        String fileType,
        long fileSize,
        Integer version,
        String parseStatus,
        String chunkStatus,
        Integer chunkCount,
        String errorMsg,
        Instant createdAt,
        Instant updatedAt) {
}
