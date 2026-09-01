package com.example.rag.doc.dto;

import jakarta.validation.constraints.Size;

/**
 * 更新知识库请求体。所有字段可选，null 值会被忽略。
 *
 * @param name        知识库名称（可选，最长 120 字符）
 * @param description 知识库描述（可选，最长 2000 字符）
 * @param category    知识库分类（可选）
 * @param visibility  可见性（可选：PUBLIC/DEPARTMENT/PRIVATE）
 */
public record UpdateKnowledgeBaseRequest(
        @Size(max = 120, message = "知识库名称不能超过 120 个字符")
        String name,

        @Size(max = 2000, message = "知识库描述不能超过 2000 个字符")
        String description,

        String category,

        String visibility) {
}
