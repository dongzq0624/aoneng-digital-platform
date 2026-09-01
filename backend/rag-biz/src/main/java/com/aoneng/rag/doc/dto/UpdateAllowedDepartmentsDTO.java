package com.aoneng.rag.doc.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * 更新知识库允许访问的部门请求体。
 *
 * @param departmentIds 新的部门 ID 列表（必填，不可为空）
 */
public record UpdateAllowedDepartmentsDTO(
        @NotNull(message = "部门 ID 列表不能为空")
        List<Long> departmentIds) {
}
