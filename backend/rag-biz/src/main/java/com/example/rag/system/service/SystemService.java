package com.example.rag.system.service;

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

import java.util.List;

/**
 * 系统管理服务接口。负责用户、部门、角色、菜单的业务规则处理
 * （权限校验、DTO 转换、数据映射）。控制器应依赖此接口而非实现类。
 */
public interface SystemService {

    // -------- 用户管理 --------

    /**
     * 获取用户列表。
     *
     * @return 用户列表
     */
    List<UserResponse> listUsers();

    /**
     * 获取用户详情。
     *
     * @param id 用户 ID
     * @return 用户信息
     */
    UserResponse getUser(long id);

    /**
     * 创建新用户。
     *
     * @param req 用户创建请求
     * @return 创建的用户信息
     */
    UserResponse createUser(CreateUserRequest req);

    /**
     * 更新用户信息。
     *
     * @param id  用户 ID
     * @param req 更新内容
     * @return 更新后的用户信息
     */
    UserResponse updateUser(long id, UpdateUserRequest req);

    /**
     * 删除用户。
     *
     * @param id 用户 ID
     */
    void deleteUser(long id);

    /**
     * 重置用户密码。
     *
     * @param id 用户 ID
     */
    void resetPassword(long id);

    // -------- 部门管理 --------

    /**
     * 获取部门列表。
     *
     * @return 部门列表
     */
    List<DepartmentResponse> listDepartments();

    /**
     * 创建新部门。
     *
     * @param req 部门创建请求
     * @return 创建的部门信息
     */
    DepartmentResponse createDepartment(CreateDepartmentRequest req);

    /**
     * 更新部门信息。
     *
     * @param id  部门 ID
     * @param req 更新内容
     * @return 更新后的部门信息
     */
    DepartmentResponse updateDepartment(long id, UpdateDepartmentRequest req);

    /**
     * 删除部门。
     *
     * @param id 部门 ID
     */
    void deleteDepartment(long id);

    // -------- 角色管理 --------

    /**
     * 获取角色列表。
     *
     * @param username 当前登录用户名
     * @return 角色列表
     */
    List<RoleResponse> listRoles(String username);

    /**
     * 创建新角色。
     *
     * @param username 当前登录用户名
     * @param req     角色创建请求
     * @return 创建的角色信息
     */
    RoleResponse createRole(String username, CreateRoleRequest req);

    /**
     * 更新角色信息。
     *
     * @param username 当前登录用户名
     * @param id      角色 ID
     * @param req     更新内容
     * @return 更新后的角色信息
     */
    RoleResponse updateRole(String username, long id, UpdateRoleRequest req);

    /**
     * 删除角色。
     *
     * @param username 当前登录用户名
     * @param id      角色 ID
     */
    void deleteRole(String username, long id);

    /**
     * 获取角色的菜单权限列表。
     *
     * @param username 当前登录用户名
     * @param id      角色 ID
     * @return 菜单 ID 列表
     */
    RoleMenuResponse getRoleMenus(String username, long id);

    /**
     * 更新角色的菜单权限。
     *
     * @param username 当前登录用户名
     * @param id      角色 ID
     * @param req     菜单 ID 列表
     */
    void updateRoleMenus(String username, long id, UpdateRoleMenusRequest req);

    // -------- 菜单管理 --------

    /**
     * 获取菜单列表。
     *
     * @param username 当前登录用户名
     * @return 菜单列表
     */
    List<MenuResponse> listMenus(String username);

    /**
     * 获取当前用户的菜单权限 ID 列表。
     *
     * @param username 当前登录用户名
     * @return 菜单 ID 列表
     */
    UserMenuResponse listUserMenuIds(String username);

    /**
     * 创建新菜单。
     *
     * @param username 当前登录用户名
     * @param req     菜单创建请求
     * @return 创建的菜单信息
     */
    MenuResponse createMenu(String username, CreateMenuRequest req);

    /**
     * 更新菜单信息。
     *
     * @param username 当前登录用户名
     * @param id      菜单 ID
     * @param req     更新内容
     * @return 更新后的菜单信息
     */
    MenuResponse updateMenu(String username, long id, UpdateMenuRequest req);

    /**
     * 删除菜单。
     *
     * @param username 当前登录用户名
     * @param id      菜单 ID
     */
    void deleteMenu(String username, long id);

    // -------- 权限校验 --------

    /**
     * 校验用户是否为平台管理员。非管理员时抛出 {@code ForbiddenException}。
     *
     * @param username 用户名
     */
    void requireAdmin(String username);
}
