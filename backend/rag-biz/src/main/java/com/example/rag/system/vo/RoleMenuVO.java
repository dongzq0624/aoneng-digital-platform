package com.example.rag.system.vo;

import java.util.List;

/**
 * 角色已分配的菜单 ID 列表响应体。
 *
 * @param id      角色 ID
 * @param menuIds 角色已分配的菜单 ID 列表
 */
public record RoleMenuVO(long id, List<Long> menuIds) {
}
