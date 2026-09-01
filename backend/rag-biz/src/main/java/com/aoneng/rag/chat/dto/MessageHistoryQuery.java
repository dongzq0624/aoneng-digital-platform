package com.aoneng.rag.chat.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * 会话消息历史分页查询条件。
 *
 * @param before   游标，传入上一页最早消息的 ID（可空表示最新页）
 * @param pageSize 每页数量，1-50 之间，默认 30
 */
public record MessageHistoryQuery(
        String before,

        @Min(value = 1, message = "pageSize 必须大于 0")
        @Max(value = 50, message = "pageSize 不能超过 50")
        Integer pageSize) {

    public int pageSizeOrDefault() {
        return pageSize == null ? 30 : pageSize;
    }
}