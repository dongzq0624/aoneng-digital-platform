package com.example.rag.doc.dto;

import java.time.Instant;

/**
 * 知识库文档响应体。
 *
 * @param id          文档 ID
 * @param kbId        所属知识库 ID
 * @param fileName    文件名
 * @param fileType    文件扩展名（如 pdf、docx）
 * @param fileSize    文件大小（字节）
 * @param version     文档版本号（可空）
 * @param parseStatus 解析状态（PENDING/PARSING/SUCCESS/FAILED）
 * @param chunkStatus 分块状态（PENDING/INDEXING/INDEXED/FAILED）
 * @param chunkCount  已生成的分块数量
 * @param errorMsg    错误信息（处理失败时填写）
 * @param createdAt   上传时间
 * @param updatedAt   最近更新时间
 */
public record KnowledgeBaseDocumentResponse(
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
