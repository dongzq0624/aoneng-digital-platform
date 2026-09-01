package com.example.rag.system.service.impl;

import com.example.rag.common.exception.ForbiddenException;
import com.example.rag.convert.OrgConvert;
import com.example.rag.service.OrgRecords;
import com.example.rag.service.PlatformRepository;
import com.example.rag.system.dto.CreateDepartmentRequest;
import com.example.rag.system.dto.CreateMenuRequest;
import com.example.rag.system.dto.CreateRoleRequest;
import com.example.rag.system.dto.CreateUserRequest;
import com.example.rag.system.dto.DepartmentResponse;
import com.example.rag.system.dto.MenuResponse;
import com.example.rag.system.dto.RoleMenuResponse;
import com.example.rag.system.dto.RoleResponse;
import com.example.rag.system.dto.UpdateDepartmentRequest;
import com.example.rag.system.dto.UpdateMenuRequest;
import com.example.rag.system.dto.UpdateRoleMenusRequest;
import com.example.rag.system.dto.UpdateRoleRequest;
import com.example.rag.system.dto.UpdateUserRequest;
import com.example.rag.system.dto.UserMenuResponse;
import com.example.rag.system.dto.UserResponse;
import com.example.rag.system.service.SystemService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 系统管理服务默认实现。
 * 持久化操作委托给 {@link PlatformRepository}，DTO 转换通过 {@link OrgConvert} 完成。
 * 管理员权限校验抛出 {@link ForbiddenException}，
 * 仓库层的 {@link IllegalStateException} / {@link IllegalArgumentException} 会传播到全局异常处理器映射为 409/400。
 */
@Service
public class SystemServiceImpl implements SystemService {

    private final PlatformRepository repo;

    public SystemServiceImpl(PlatformRepository repo) {
        this.repo = repo;
    }

    // -------- 用户管理 --------

    /**
     * 获取用户列表。
     */
    @Override
    public List<UserResponse> listUsers() {
        return OrgConvert.INSTANCE.toUserResponses(repo.users());
    }

    /**
     * 获取用户详情。
     */
    @Override
    public UserResponse getUser(long id) {
        return OrgConvert.INSTANCE.toUserResponse(repo.userById(id));
    }

    /**
     * 创建新用户。
     */
    @Override
    public UserResponse createUser(CreateUserRequest req) {
        return OrgConvert.INSTANCE.toUserResponse(repo.createUser(OrgConvert.INSTANCE.toPayload(req)));
    }

    /**
     * 更新用户信息。
     */
    @Override
    public UserResponse updateUser(long id, UpdateUserRequest req) {
        return OrgConvert.INSTANCE.toUserResponse(repo.updateUser(id, OrgConvert.INSTANCE.toPayload(req)));
    }

    /**
     * 删除用户。
     */
    @Override
    public void deleteUser(long id) {
        repo.deleteUser(id);
    }

    /**
     * 重置用户密码。
     */
    @Override
    public void resetPassword(long id) {
        repo.resetPassword(id);
    }

    // -------- 部门管理 --------

    /**
     * 获取部门列表。
     */
    @Override
    public List<DepartmentResponse> listDepartments() {
        return OrgConvert.INSTANCE.toDepartmentResponses(repo.departments());
    }

    /**
     * 创建新部门。
     */
    @Override
    public DepartmentResponse createDepartment(CreateDepartmentRequest req) {
        return OrgConvert.INSTANCE.toDepartmentResponse(repo.createDepartment(OrgConvert.INSTANCE.toPayload(req)));
    }

    /**
     * 更新部门信息。
     */
    @Override
    public DepartmentResponse updateDepartment(long id, UpdateDepartmentRequest req) {
        return OrgConvert.INSTANCE.toDepartmentResponse(repo.updateDepartment(id, OrgConvert.INSTANCE.toPayload(req)));
    }

    /**
     * 删除部门。
     */
    @Override
    public void deleteDepartment(long id) {
        repo.deleteDepartment(id);
    }

    // -------- 角色管理 --------

    /**
     * 获取角色列表（仅管理员可访问）。
     */
    @Override
    public List<RoleResponse> listRoles(String username) {
        requireAdmin(username);
        return OrgConvert.INSTANCE.toRoleResponses(repo.roles());
    }

    /**
     * 创建新角色（仅管理员可操作）。
     */
    @Override
    public RoleResponse createRole(String username, CreateRoleRequest req) {
        requireAdmin(username);
        return OrgConvert.INSTANCE.toRoleResponse(repo.createRole(OrgConvert.INSTANCE.toPayload(req)));
    }

    /**
     * 更新角色信息（仅管理员可操作）。
     */
    @Override
    public RoleResponse updateRole(String username, long id, UpdateRoleRequest req) {
        requireAdmin(username);
        return OrgConvert.INSTANCE.toRoleResponse(repo.updateRole(id, OrgConvert.INSTANCE.toPayload(req)));
    }

    /**
     * 删除角色（仅管理员可操作）。
     */
    @Override
    public void deleteRole(String username, long id) {
        requireAdmin(username);
        repo.deleteRole(id);
    }

    /**
     * 获取角色的菜单权限列表（仅管理员可访问）。
     */
    @Override
    public RoleMenuResponse getRoleMenus(String username, long id) {
        requireAdmin(username);
        return new RoleMenuResponse(id, repo.roleMenuIds(id));
    }

    /**
     * 更新角色的菜单权限（仅管理员可操作）。
     */
    @Override
    public void updateRoleMenus(String username, long id, UpdateRoleMenusRequest req) {
        requireAdmin(username);
        repo.updateRoleMenus(id, OrgConvert.INSTANCE.roleMenuPayload(req.menuIds()));
    }

    // -------- 菜单管理 --------

    /**
     * 获取菜单列表（仅管理员可访问）。
     */
    @Override
    public List<MenuResponse> listMenus(String username) {
        requireAdmin(username);
        return OrgConvert.INSTANCE.toMenuResponses(repo.menus());
    }

    /**
     * 获取当前用户的菜单权限 ID 列表。
     */
    @Override
    public UserMenuResponse listUserMenuIds(String username) {
        return new UserMenuResponse(repo.userMenuIds(username));
    }

    /**
     * 创建新菜单（仅管理员可操作）。
     */
    @Override
    public MenuResponse createMenu(String username, CreateMenuRequest req) {
        requireAdmin(username);
        return OrgConvert.INSTANCE.toMenuResponse(repo.createMenu(OrgConvert.INSTANCE.toPayload(req)));
    }

    /**
     * 更新菜单信息（仅管理员可操作）。
     */
    @Override
    public MenuResponse updateMenu(String username, long id, UpdateMenuRequest req) {
        requireAdmin(username);
        return OrgConvert.INSTANCE.toMenuResponse(repo.updateMenu(id, OrgConvert.INSTANCE.toPayload(req)));
    }

    /**
     * 删除菜单（仅管理员可操作）。
     */
    @Override
    public void deleteMenu(String username, long id) {
        requireAdmin(username);
        repo.deleteMenu(id);
    }

    // -------- 权限校验 --------

    /**
     * 校验用户是否为平台管理员。非管理员时抛出 {@link ForbiddenException}。
     */
    @Override
    public void requireAdmin(String username) {
        if (username == null || !"admin".equals(username)) {
            throw new ForbiddenException("仅系统管理员可管理角色和菜单");
        }
    }

    /**
     * 根据 ID 获取用户，简化调用方处理仓库层抛出异常的逻辑。
     */
    public OrgRecords.UserRow userOrThrow(long id) {
        return repo.userById(id);
    }
}
