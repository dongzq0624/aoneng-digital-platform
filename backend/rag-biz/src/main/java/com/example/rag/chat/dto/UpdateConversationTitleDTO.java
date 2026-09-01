package com.example.rag.chat.dto;

import jakarta.validation.constraints.Size;

/**
 * 更新会话标题请求体。
 *
 * @param title 新的会话标题（可选，最长 120 字符）
 */
public record UpdateConversationTitleDTO(
        @Size(max = 120, message = "会话标题不能超过 120 个字符")
        String title) {
}
