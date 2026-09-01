package com.example.rag.convert;

import com.example.rag.system.dto.CreateDepartmentRequest;
import com.example.rag.system.dto.CreateMenuRequest;
import com.example.rag.system.dto.CreateRoleRequest;
import com.example.rag.system.dto.CreateUserRequest;
import com.example.rag.system.dto.DepartmentResponse;
import com.example.rag.system.dto.MenuResponse;
import com.example.rag.system.dto.RoleResponse;
import com.example.rag.system.dto.UpdateDepartmentRequest;
import com.example.rag.system.dto.UpdateMenuRequest;
import com.example.rag.system.dto.UpdateRoleRequest;
import com.example.rag.system.dto.UpdateUserRequest;
import com.example.rag.system.dto.UserResponse;
import com.example.rag.service.OrgRecords;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 组织 / 用户 / 角色 / 菜单相关转换器。把 {@link com.example.rag.service.OrgRecords} 行映射为
 * 控制器 DTO，并把请求 DTO 转换为 {@code PlatformRepository} 接受的 {@code Map<String, Object>}
  * 参数载荷。取代了过去散落在控制器里的 {@code toXxxResponse} / {@code xxxPayload} 辅助方法。
 */
@Mapper
public interface OrgConvert {

    /** 单例 Mapper 实例。 */
    OrgConvert INSTANCE = Mappers.getMapper(OrgConvert.class);

    // -------- 行 -> 响应 --------

    /** 行 → 响应映射（用户 / 部门 / 角色 / 菜单）。批量变体由 MapStruct 自动生成。 */
    UserResponse toUserResponse(OrgRecords.UserRow row);

    /** 用户批量转换。 */
    List<UserResponse> toUserResponses(List<OrgRecords.UserRow> rows);

    /** 单条部门行转响应。 */
    DepartmentResponse toDepartmentResponse(OrgRecords.DepartmentRow row);

    /** 部门批量转换。 */
    List<DepartmentResponse> toDepartmentResponses(List<OrgRecords.DepartmentRow> rows);

    /** 单条角色行转响应。 */
    RoleResponse toRoleResponse(OrgRecords.RoleRow row);

    /** 角色批量转换。 */
    List<RoleResponse> toRoleResponses(List<OrgRecords.RoleRow> rows);

    /** 单条菜单行转响应。 */
    MenuResponse toMenuResponse(OrgRecords.MenuRow row);

    /** 菜单批量转换。 */
    List<MenuResponse> toMenuResponses(List<OrgRecords.MenuRow> rows);

    // -------- 请求 DTO -> Map 参数载荷（传给 PlatformRepository） --------

    default Map<String, Object> toPayload(CreateUserRequest req) {
        Map<String, Object> p = new HashMap<>();
        p.put("employeeNo", req.employeeNo());
        p.put("username", req.username());
        p.put("realName", req.realName());
        p.put("email", req.email());
        p.put("phone", req.phone());
        p.put("deptId", req.deptId());
        p.put("status", req.status());
        return p;
    }

    default Map<String, Object> toPayload(UpdateUserRequest req) {
        Map<String, Object> p = new HashMap<>();
        p.put("realName", req.realName());
        p.put("email", req.email());
        p.put("phone", req.phone());
        p.put("deptId", req.deptId());
        p.put("status", req.status());
        return p;
    }

    default Map<String, Object> toPayload(CreateDepartmentRequest req) {
        Map<String, Object> p = new HashMap<>();
        p.put("name", req.name());
        p.put("parentId", req.parentId());
        p.put("sort", req.sort());
        p.put("status", req.status());
        return p;
    }

    default Map<String, Object> toPayload(UpdateDepartmentRequest req) {
        Map<String, Object> p = new HashMap<>();
        p.put("name", req.name());
        p.put("parentId", req.parentId());
        p.put("sort", req.sort());
        p.put("status", req.status());
        return p;
    }

    default Map<String, Object> toPayload(CreateRoleRequest req) {
        Map<String, Object> p = new HashMap<>();
        p.put("code", req.code());
        p.put("name", req.name());
        p.put("remark", req.remark());
        p.put("status", req.status());
        return p;
    }

    default Map<String, Object> toPayload(UpdateRoleRequest req) {
        Map<String, Object> p = new HashMap<>();
        p.put("code", req.code());
        p.put("name", req.name());
        p.put("remark", req.remark());
        p.put("status", req.status());
        return p;
    }

    default Map<String, Object> toPayload(CreateMenuRequest req) {
        Map<String, Object> p = new HashMap<>();
        p.put("name", req.name());
        p.put("parentId", req.parentId());
        p.put("perms", req.perms());
        p.put("path", req.path());
        p.put("sort", req.sort());
        p.put("status", req.status());
        return p;
    }

    default Map<String, Object> toPayload(UpdateMenuRequest req) {
        Map<String, Object> p = new HashMap<>();
        p.put("name", req.name());
        p.put("parentId", req.parentId());
        p.put("perms", req.perms());
        p.put("path", req.path());
        p.put("sort", req.sort());
        p.put("status", req.status());
        return p;
    }

    /** 角色菜单授权 payload。{@code menuIds} 在 null 时输出空列表，保持参数化 SQL 行为一致。 */
    default Map<String, Object> roleMenuPayload(List<Long> menuIds) {
        return Map.of("menuIds", menuIds == null ? List.of() : menuIds);
    }
}
