package com.example.rag.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * 创建菜单请求体。
 *
 * @param name     菜单名称（必填，最长 64 字符）
 * @param parentId 上级菜单 ID（可选）
 * @param perms    权限标识（如 kb:create，可选，最长 100 字符）
 * @param path     路由路径（可选，最长 200 字符）
 * @param sort     显示排序（可选）
 * @param status   启用状态（可选，1=启用，0=停用）
 */
public record CreateMenuDTO(
        @NotBlank(message = "菜单名称不能为空")
        @Size(max = 64, message = "菜单名称长度不能超过 64 个字符")
        String name,

        @Positive(message = "上级菜单 ID 必须为正数")
        Long parentId,

        @Size(max = 100, message = "权限标识长度不能超过 100 个字符")
        String perms,

        @Size(max = 200, message = "路由路径长度不能超过 200 个字符")
        String path,

        Integer sort,
        Integer status) {
}
