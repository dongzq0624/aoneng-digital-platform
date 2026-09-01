package com.example.rag.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * RAG 对话轮次请求体。取代了之前控制器中直接使用的 {@code Map<String, Object>}。
 *
 * @param question       用户问题（必填，最大 4000 字符）
 * @param conversationId 会话 ID（可空，空表示新建会话）
 * @param kbIds          限定检索的知识库 ID 列表（可为 null 或空列表表示使用全部可访问知识库）
 */
public record ChatDTO(
        @NotBlank(message = "问题不能为空")
        @Size(max = 4000, message = "问题长度不能超过 4000 个字符")
        String question,

        Long conversationId,

        List<Long> kbIds) {
}
