package com.example.rag.chat.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * 会话列表分页查询条件。
 *
 * @param cursor   游标，取上一页响应中的 {@code nextCursor}（可空表示第一页）
 * @param pageSize 每页数量，1-50 之间，默认 20
 */
public record ConversationPageQuery(
        String cursor,

        @Min(value = 1, message = "pageSize 必须大于 0")
        @Max(value = 50, message = "pageSize 不能超过 50")
        Integer pageSize) {

    public int pageSizeOrDefault() {
        return pageSize == null ? 20 : pageSize;
    }
}