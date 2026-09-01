package com.example.rag.doc.dto;

import java.time.Instant;
import java.util.List;

/**
 * 知识库响应体。包含知识库的基本信息、可见性、所属部门、
 * 分块参数、文档数量、用户权限标志等。
 */
public record KnowledgeBaseResponse(
        long id,
        String name,
        String description,
        String category,
        String visibility,
        long ownerId,
        Long deptId,
        Integer chunkSize,
        Integer chunkOverlap,
        Integer docCount,
        List<Long> allowedDeptIds,
        boolean canManage,
        boolean canConfigureDepartments,
        Instant createdAt,
        Instant updatedAt) {
}
