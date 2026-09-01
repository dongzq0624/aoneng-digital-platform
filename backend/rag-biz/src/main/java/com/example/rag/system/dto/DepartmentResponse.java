package com.example.rag.system.dto;

import java.time.Instant;

/**
 * 部门响应体。
 *
 * @param id         部门 ID
 * @param parentId   上级部门 ID
 * @param name       部门名称
 * @param ancestors  祖先路径（如 "/1/3/"，根部门为 "/1/"）
 * @param sort       显示排序
 * @param status     启用状态（1=启用，0=停用）
 * @param userCount  部门下用户数量
 * @param childCount 子部门数量
 * @param createdAt  创建时间
 */
public record DepartmentResponse(
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
