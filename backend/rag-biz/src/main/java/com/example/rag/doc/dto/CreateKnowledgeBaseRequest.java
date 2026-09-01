package com.example.rag.doc.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * 创建知识库请求体。
 *
 * @param name         知识库名称（必填，最长 120 字符）
 * @param description  知识库描述（可选，最长 2000 字符）
 * @param category     知识库分类（可选，最长 64 字符）
 * @param visibility   可见性（必填：PUBLIC/DEPARTMENT/PRIVATE）
 * @param chunkSize    文本分块大小（必填，需为正数）
 * @param chunkOverlap 分块重叠字符数（必填，需为正数）
 */
public record CreateKnowledgeBaseRequest(
        @NotBlank(message = "知识库名称不能为空")
        @Size(max = 120, message = "知识库名称不能超过 120 个字符")
        String name,

        @Size(max = 2000, message = "知识库描述不能超过 2000 个字符")
        String description,

        @Size(max = 64, message = "分类长度不能超过 64 个字符")
        String category,

        @NotBlank(message = "可见范围不能为空")
        String visibility,

        @Positive(message = "chunkSize 必须为正数")
        Integer chunkSize,

        @Positive(message = "chunkOverlap 必须为正数")
        Integer chunkOverlap) {
}
