package com.example.rag.system.service.impl;

import com.example.rag.auth.convert.OrgConvert;
import com.example.rag.common.exception.ForbiddenException;
import com.example.rag.domain.auth.OrgRecords;
import com.example.rag.service.PlatformRepository;
import com.example.rag.system.dto.CreateDepartmentDTO;
import com.example.rag.system.dto.CreateMenuDTO;
import com.example.rag.system.dto.CreateRoleDTO;
import com.example.rag.system.dto.CreateUserDTO;
import com.example.rag.system.dto.UpdateDepartmentDTO;
import com.example.rag.system.dto.UpdateMenuDTO;
import com.example.rag.system.dto.UpdateRoleMenusDTO;
import com.example.rag.system.dto.UpdateRoleDTO;
import com.example.rag.system.dto.UpdateUserDTO;
import com.example.rag.system.service.SystemService;
import com.example.rag.system.vo.DepartmentVO;
import com.example.rag.system.vo.MenuVO;
import com.example.rag.system.vo.RoleMenuVO;
import com.example.rag.system.vo.RoleVO;
import com.example.rag.system.vo.UserMenuVO;
import com.example.rag.system.vo.UserVO;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 系统管理服务默认实现。
 * 持久化操作委托给 {@link PlatformRepository}，VO 转换通过 {@link OrgConvert} 完成。
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

    @Override
    public List<UserVO> listUsers() {
        return OrgConvert.INSTANCE.toUserResponses(repo.users());
    }

    @Override
    public UserVO getUser(long id) {
        return OrgConvert.INSTANCE.toUserResponse(repo.userById(id));
    }

    @Override
    public UserVO createUser(CreateUserDTO req) {
        return OrgConvert.INSTANCE.toUserResponse(repo.createUser(OrgConvert.INSTANCE.toPayload(req)));
    }

    @Override
    public UserVO updateUser(long id, UpdateUserDTO req) {
        return OrgConvert.INSTANCE.toUserResponse(repo.updateUser(id, OrgConvert.INSTANCE.toPayload(req)));
    }

    @Override
    public void deleteUser(long id) {
        repo.deleteUser(id);
    }

    @Override
    public void resetPassword(long id) {
        repo.resetPassword(id);
    }

    // -------- 部门管理 --------

    @Override
    public List<DepartmentVO> listDepartments() {
        return OrgConvert.INSTANCE.toDepartmentResponses(repo.departments());
    }

    @Override
    public DepartmentVO createDepartment(CreateDepartmentDTO req) {
        return OrgConvert.INSTANCE.toDepartmentResponse(repo.createDepartment(OrgConvert.INSTANCE.toPayload(req)));
    }

    @Override
    public DepartmentVO updateDepartment(long id, UpdateDepartmentDTO req) {
        return OrgConvert.INSTANCE.toDepartmentResponse(repo.updateDepartment(id, OrgConvert.INSTANCE.toPayload(req)));
    }

    @Override
    public void deleteDepartment(long id) {
        repo.deleteDepartment(id);
    }

    // -------- 角色管理 --------

    @Override
    public List<RoleVO> listRoles(String username) {
        requireAdmin(username);
        return OrgConvert.INSTANCE.toRoleResponses(repo.roles());
    }

    @Override
    public RoleVO createRole(String username, CreateRoleDTO req) {
        requireAdmin(username);
        return OrgConvert.INSTANCE.toRoleResponse(repo.createRole(OrgConvert.INSTANCE.toPayload(req)));
    }

    @Override
    public RoleVO updateRole(String username, long id, UpdateRoleDTO req) {
        requireAdmin(username);
        return OrgConvert.INSTANCE.toRoleResponse(repo.updateRole(id, OrgConvert.INSTANCE.toPayload(req)));
    }

    @Override
    public void deleteRole(String username, long id) {
        requireAdmin(username);
        repo.deleteRole(id);
    }

    @Override
    public RoleMenuVO getRoleMenus(String username, long id) {
        requireAdmin(username);
        return new RoleMenuVO(id, repo.roleMenuIds(id));
    }

    @Override
    public void updateRoleMenus(String username, long id, UpdateRoleMenusDTO req) {
        requireAdmin(username);
        repo.updateRoleMenus(id, OrgConvert.INSTANCE.roleMenuPayload(req.menuIds()));
    }

    // -------- 菜单管理 --------

    @Override
    public List<MenuVO> listMenus(String username) {
        requireAdmin(username);
        return OrgConvert.INSTANCE.toMenuResponses(repo.menus());
    }

    @Override
    public UserMenuVO listUserMenuIds(String username) {
        return new UserMenuVO(repo.userMenuIds(username));
    }

    @Override
    public MenuVO createMenu(String username, CreateMenuDTO req) {
        requireAdmin(username);
        return OrgConvert.INSTANCE.toMenuResponse(repo.createMenu(OrgConvert.INSTANCE.toPayload(req)));
    }

    @Override
    public MenuVO updateMenu(String username, long id, UpdateMenuDTO req) {
        requireAdmin(username);
        return OrgConvert.INSTANCE.toMenuResponse(repo.updateMenu(id, OrgConvert.INSTANCE.toPayload(req)));
    }

    @Override
    public void deleteMenu(String username, long id) {
        requireAdmin(username);
        repo.deleteMenu(id);
    }

    // -------- 权限校验 --------

    private void requireAdmin(String username) {
        boolean admin = repo.kbScope(username).admin();
        if (!admin) throw new ForbiddenException("仅系统管理员可访问该资源");
    }
}
