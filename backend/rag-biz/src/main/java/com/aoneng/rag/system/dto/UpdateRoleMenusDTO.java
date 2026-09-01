package com.aoneng.rag.system.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * 替换角色菜单分配请求体。
 *
 * @param menuIds 新的菜单 ID 列表（必填，不可为空）
 */
public record UpdateRoleMenusDTO(
        @NotNull(message = "菜单 ID 列表不能为空")
        List<Long> menuIds) {
}
