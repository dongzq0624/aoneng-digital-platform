package com.example.rag.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * 创建部门请求体。
 *
 * @param name     部门名称（必填，最长 64 字符）
 * @param parentId 上级部门 ID（可选；顶级部门时为正数根节点）
 * @param sort     显示排序（可选）
 * @param status   启用状态（可选，1=启用，0=停用）
 */
public record CreateDepartmentDTO(
        @NotBlank(message = "部门名称不能为空")
        @Size(max = 64, message = "部门名称长度不能超过 64 个字符")
        String name,

        @Positive(message = "上级部门 ID 必须为正数")
        Long parentId,

        Integer sort,
        Integer status) {
}
