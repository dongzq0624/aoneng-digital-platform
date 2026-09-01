package com.example.rag.auth.convert;

import com.example.rag.system.dto.CreateDepartmentDTO;
import com.example.rag.system.dto.CreateMenuDTO;
import com.example.rag.system.dto.CreateRoleDTO;
import com.example.rag.system.dto.CreateUserDTO;
import com.example.rag.system.dto.UpdateDepartmentDTO;
import com.example.rag.system.dto.UpdateMenuDTO;
import com.example.rag.system.dto.UpdateRoleDTO;
import com.example.rag.system.dto.UpdateUserDTO;
import com.example.rag.system.vo.DepartmentVO;
import com.example.rag.system.vo.MenuVO;
import com.example.rag.system.vo.RoleVO;
import com.example.rag.system.vo.UserVO;
import com.example.rag.domain.auth.OrgRecords;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 组织 / 用户 / 角色 / 菜单相关转换器。把 {@link OrgRecords} 行映射为
 * 控制器 VO，并把请求 DTO 转换为 {@code PlatformRepository} 接受的 {@code Map<String, Object>}
 * 参数载荷。取代了过去散落在控制器里的 {@code toXxxResponse} / {@code xxxPayload} 辅助方法。
 */
@Mapper
public interface OrgConvert {

    /** 单例 Mapper 实例。 */
    OrgConvert INSTANCE = Mappers.getMapper(OrgConvert.class);

    // -------- 行 -> 响应 VO --------

    UserVO toUserResponse(OrgRecords.UserRow row);

    List<UserVO> toUserResponses(List<OrgRecords.UserRow> rows);

    DepartmentVO toDepartmentResponse(OrgRecords.DepartmentRow row);

    List<DepartmentVO> toDepartmentResponses(List<OrgRecords.DepartmentRow> rows);

    RoleVO toRoleResponse(OrgRecords.RoleRow row);

    List<RoleVO> toRoleResponses(List<OrgRecords.RoleRow> rows);

    MenuVO toMenuResponse(OrgRecords.MenuRow row);

    List<MenuVO> toMenuResponses(List<OrgRecords.MenuRow> rows);

    // -------- 请求 DTO -> Map 参数载荷（传给 PlatformRepository） --------

    default Map<String, Object> toPayload(CreateUserDTO req) {
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

    default Map<String, Object> toPayload(UpdateUserDTO req) {
        Map<String, Object> p = new HashMap<>();
        p.put("realName", req.realName());
        p.put("email", req.email());
        p.put("phone", req.phone());
        p.put("deptId", req.deptId());
        p.put("status", req.status());
        return p;
    }

    default Map<String, Object> toPayload(CreateDepartmentDTO req) {
        Map<String, Object> p = new HashMap<>();
        p.put("name", req.name());
        p.put("parentId", req.parentId());
        p.put("sort", req.sort());
        p.put("status", req.status());
        return p;
    }

    default Map<String, Object> toPayload(UpdateDepartmentDTO req) {
        Map<String, Object> p = new HashMap<>();
        p.put("name", req.name());
        p.put("parentId", req.parentId());
        p.put("sort", req.sort());
        p.put("status", req.status());
        return p;
    }

    default Map<String, Object> toPayload(CreateRoleDTO req) {
        Map<String, Object> p = new HashMap<>();
        p.put("code", req.code());
        p.put("name", req.name());
        p.put("remark", req.remark());
        p.put("status", req.status());
        return p;
    }

    default Map<String, Object> toPayload(UpdateRoleDTO req) {
        Map<String, Object> p = new HashMap<>();
        p.put("code", req.code());
        p.put("name", req.name());
        p.put("remark", req.remark());
        p.put("status", req.status());
        return p;
    }

    default Map<String, Object> toPayload(CreateMenuDTO req) {
        Map<String, Object> p = new HashMap<>();
        p.put("name", req.name());
        p.put("parentId", req.parentId());
        p.put("perms", req.perms());
        p.put("path", req.path());
        p.put("sort", req.sort());
        p.put("status", req.status());
        return p;
    }

    default Map<String, Object> toPayload(UpdateMenuDTO req) {
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
