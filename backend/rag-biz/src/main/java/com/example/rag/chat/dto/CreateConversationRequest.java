package com.example.rag.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 创建新会话请求体。
 *
 * @param title 会话标题（可选，最长 120 字符）
 * @param kbIds 关联的知识库 ID 列表（可选；空列表或 null 表示使用全部可访问知识库）
 */
public record CreateConversationRequest(
        @Size(max = 120, message = "会话标题不能超过 120 个字符")
        String title,

        List<Long> kbIds) {
}
