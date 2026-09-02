package com.aoneng.rag.doc.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** Request to create a knowledge base. Chunk values are parent-token settings. */
public record CreateKnowledgeBaseDTO(
        @NotBlank(message = "知识库名称不能为空")
        @Size(max = 120, message = "知识库名称不能超过 120 个字符")
        String name,

        @Size(max = 2000, message = "知识库描述不能超过 2000 个字符")
        String description,

        @Size(max = 64, message = "分类长度不能超过 64 个字符")
        String category,

        @NotBlank(message = "可见范围不能为空")
        String visibility,

        @Positive(message = "chunkSize（父块 token 数）必须为正数")
        @Max(value = 10000, message = "chunkSize（父块 token 数）不能超过 10000")
        Integer chunkSize,

        @PositiveOrZero(message = "chunkOverlap（父块 token 数）不能为负数")
        @Max(value = 5000, message = "chunkOverlap（父块 token 数）不能超过 5000")
        Integer chunkOverlap) {
}
