package com.example.rag.common.result;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * 分页结果包装。前端列表组件可直接消费 {@link #items}，
 * 通过 {@link #total}、{@link #page}、{@link #pageSize} 渲染分页器。
 *
 * @param total    满足条件的总条数
 * @param page     当前页码，从 1 开始
 * @param pageSize 每页大小
 * @param items    当前页数据
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PageResult<T>(long total, int page, int pageSize, List<T> items) {
}