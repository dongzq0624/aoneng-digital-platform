package com.example.rag.system.vo;

import java.time.Instant;

/**
 * 部门响应体。
 */
public record DepartmentVO(
        long id,
        long parentId,
        String name,
        String ancestors,
        Integer sort,
        Integer status,
        Integer userCount,
        Integer childCount,
        Instant createdAt) {
}
