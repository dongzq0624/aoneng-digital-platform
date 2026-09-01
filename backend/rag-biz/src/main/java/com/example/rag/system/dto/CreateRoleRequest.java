package com.example.rag.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 创建角色请求体。
 *
 * @param code   角色编码（必填，3-64 位大写字母、数字或下划线）
 * @param name   角色名称（必填，最长 64 字符）
 * @param remark 备注（可选，最长 500 字符）
 * @param status 启用状态（可选，1=启用，0=停用）
 */
public record CreateRoleRequest(
        @NotBlank(message = "角色编码不能为空")
        @Pattern(regexp = "^[A-Z][A-Z0-9_]{2,63}$", message = "角色编码须为 3-64 位大写字母、数字或下划线")
        String code,

        @NotBlank(message = "角色名称不能为空")
        @Size(max = 64, message = "角色名称长度不能超过 64 个字符")
        String name,

        @Size(max = 500, message = "备注长度不能超过 500 个字符")
        String remark,

        Integer status) {
}
