package com.example.rag.chat.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 问答反馈提交请求体。
 *
 * @param qaRecordId 问答记录 ID（必填）
 * @param rating     评分（1=点赞，-1=点踩，必填）
 * @param comment    评价内容（可选，最长 1000 字符）
 */
public record FeedbackDTO(
        @NotNull(message = "问答记录 ID 不能为空")
        Long qaRecordId,

        @Min(value = -1, message = "评分只能为 1 或 -1")
        @Max(value = 1, message = "评分只能为 1 或 -1")
        Integer rating,

        @Size(max = 1000, message = "评价内容不能超过 1000 个字符")
        String comment) {
}
