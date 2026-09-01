package com.example.rag.chat.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * 问答记录分页查询条件。
 *
 * @param page     页码（从 1 开始），默认 1
 * @param pageSize 每页数量，1-100 之间，默认 20
 */
public record QaRecordPageQuery(
        @Min(value = 1, message = "页码必须大于 0")
        Integer page,

        @Min(value = 1, message = "pageSize 必须大于 0")
        @Max(value = 100, message = "pageSize 不能超过 100")
        Integer pageSize) {

    public int pageOrDefault() {
        return page == null ? 1 : page;
    }

    public int pageSizeOrDefault() {
        return pageSize == null ? 20 : pageSize;
    }
}