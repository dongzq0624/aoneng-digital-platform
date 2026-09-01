package com.example.rag.system.dto;

/**
 * 菜单响应体。
 *
 * @param id         菜单 ID
 * @param parentId   上级菜单 ID
 * @param name       菜单名称
 * @param perms      权限标识
 * @param path       路由路径
 * @param sort       显示排序
 * @param status     启用状态（1=启用，0=停用）
 * @param childCount 子菜单数量
 * @param roleCount  关联角色数量
 */
public record MenuResponse(
        long id,
        long parentId,
        String name,
        String perms,
        String path,
        Integer sort,
        Integer status,
        Integer childCount,
        Integer roleCount) {
}
