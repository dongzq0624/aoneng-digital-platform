package com.example.rag.system.dto;

/**
 * 角色响应体。
 *
 * @param id        角色 ID
 * @param code      角色编码
 * @param name      角色名称
 * @param remark    备注
 * @param status    启用状态（1=启用，0=停用）
 * @param userCount 关联用户数量
 * @param menuCount 关联菜单数量
 */
public record RoleResponse(
        long id,
        String code,
        String name,
        String remark,
        Integer status,
        Integer userCount,
        Integer menuCount) {
}
