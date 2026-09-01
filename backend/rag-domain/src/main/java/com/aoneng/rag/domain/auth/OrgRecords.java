package com.aoneng.rag.domain.auth;

/**
 * 组织管理返回值类型（Record 形式）。
 * 保持控制器签名强类型，避免裸 {@code Map<String, Object>} 泄漏到 HTTP 层。
 */
public final class OrgRecords {
    private OrgRecords() {
    }

    public record UserRow(
            long id,
            String employeeNo,
            String username,
            String realName,
            Long deptId,
            String dept,
            String role,
            Integer status) {
    }

    public record DepartmentRow(
            long id,
            long parentId,
            String name,
            String ancestors,
            Integer sort,
            Integer status,
            Integer userCount,
            Integer childCount) {
    }

    public record RoleRow(
            long id,
            String code,
            String name,
            String remark,
            Integer status,
            Integer userCount,
            Integer menuCount) {
    }

    public record MenuRow(
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
}
