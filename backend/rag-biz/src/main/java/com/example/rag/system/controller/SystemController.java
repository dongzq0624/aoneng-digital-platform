package com.example.rag.system.controller;

import com.example.rag.common.result.Result;
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
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 系统管理控制器。负责 HTTP 协议层面的请求校验和响应封装，
 * 所有用户、部门、角色、菜单等管理业务逻辑委托给 {@link SystemService}。
 */
@RestController
@RequestMapping("/api/system")
public class SystemController {

    private final SystemService systemService;

    public SystemController(SystemService systemService) {
        this.systemService = systemService;
    }

    // -------- 用户管理 --------

    /**
     * 获取用户列表。
     *
     * @return 用户列表
     */
    @GetMapping("/users")
    public Result<List<UserResponse>> users() {
        return Result.ok(systemService.listUsers());
    }

    /**
     * 获取用户详情。
     *
     * @param id 用户 ID
     * @return 用户信息
     */
    @GetMapping("/users/{id}")
    public Result<UserResponse> user(@PathVariable long id) {
        return Result.ok(systemService.getUser(id));
    }

    /**
     * 创建新用户。
     *
     * @param req 用户信息
     * @return 创建的用户信息
     */
    @PostMapping("/users")
    public Result<UserResponse> createUser(@Valid @RequestBody CreateUserRequest req) {
        return Result.ok(systemService.createUser(req));
    }

    /**
     * 更新用户信息。
     *
     * @param id  用户 ID
     * @param req 更新内容
     * @return 更新后的用户信息
     */
    @PutMapping("/users/{id}")
    public Result<UserResponse> updateUser(@PathVariable long id,
                                           @Valid @RequestBody UpdateUserRequest req) {
        return Result.ok(systemService.updateUser(id, req));
    }

    /**
     * 删除用户。
     *
     * @param id 用户 ID
     * @return 删除结果
     */
    @DeleteMapping("/users/{id}")
    public Result<Map<String, Object>> deleteUser(@PathVariable long id) {
        systemService.deleteUser(id);
        return Result.ok(Map.of("id", id, "deleted", true));
    }

    /**
     * 重置用户密码为默认密码。
     *
     * @param id 用户 ID
     * @return 重置结果
     */
    @PostMapping("/users/{id}/reset-password")
    public Result<Map<String, Object>> resetPassword(@PathVariable long id) {
        systemService.resetPassword(id);
        return Result.ok(Map.of("id", id, "reset", true));
    }

    // -------- 部门管理 --------

    /**
     * 获取部门列表（树形结构）。
     *
     * @return 部门列表
     */
    @GetMapping("/depts")
    public Result<List<DepartmentResponse>> depts() {
        return Result.ok(systemService.listDepartments());
    }

    /**
     * 创建新部门。
     *
     * @param req 部门信息
     * @return 创建的部门信息
     */
    @PostMapping("/depts")
    public Result<DepartmentResponse> createDept(@Valid @RequestBody CreateDepartmentRequest req) {
        return Result.ok(systemService.createDepartment(req));
    }

    /**
     * 更新部门信息。
     *
     * @param id  部门 ID
     * @param req 更新内容
     * @return 更新后的部门信息
     */
    @PutMapping("/depts/{id}")
    public Result<DepartmentResponse> updateDept(@PathVariable long id,
                                                @Valid @RequestBody UpdateDepartmentRequest req) {
        return Result.ok(systemService.updateDepartment(id, req));
    }

    /**
     * 删除部门。
     *
     * @param id 部门 ID
     * @return 删除结果
     */
    @DeleteMapping("/depts/{id}")
    public Result<Map<String, Object>> deleteDept(@PathVariable long id) {
        systemService.deleteDepartment(id);
        return Result.ok(Map.of("id", id, "deleted", true));
    }

    // -------- 角色管理 --------

    /**
     * 获取角色列表。
     *
     * @param username 当前登录用户名
     * @return 角色列表
     */
    @GetMapping("/roles")
    public Result<List<RoleResponse>> roles(@AuthenticationPrincipal String username) {
        return Result.ok(systemService.listRoles(username));
    }

    /**
     * 创建新角色。
     *
     * @param username 当前登录用户名
     * @param req     角色信息
     * @return 创建的角色信息
     */
    @PostMapping("/roles")
    public Result<RoleResponse> createRole(@AuthenticationPrincipal String username,
                                           @Valid @RequestBody CreateRoleRequest req) {
        return Result.ok(systemService.createRole(username, req));
    }

    /**
     * 更新角色信息。
     *
     * @param username 当前登录用户名
     * @param id      角色 ID
     * @param req     更新内容
     * @return 更新后的角色信息
     */
    @PutMapping("/roles/{id}")
    public Result<RoleResponse> updateRole(@AuthenticationPrincipal String username,
                                           @PathVariable long id,
                                           @Valid @RequestBody UpdateRoleRequest req) {
        return Result.ok(systemService.updateRole(username, id, req));
    }

    /**
     * 删除角色。
     *
     * @param username 当前登录用户名
     * @param id      角色 ID
     * @return 删除结果
     */
    @DeleteMapping("/roles/{id}")
    public Result<Map<String, Object>> deleteRole(@AuthenticationPrincipal String username,
                                                  @PathVariable long id) {
        systemService.deleteRole(username, id);
        return Result.ok(Map.of("id", id, "deleted", true));
    }

    /**
     * 获取角色的菜单权限列表。
     *
     * @param username 当前登录用户名
     * @param id      角色 ID
     * @return 菜单 ID 列表
     */
    @GetMapping("/roles/{id}/menus")
    public Result<RoleMenuResponse> roleMenus(@AuthenticationPrincipal String username,
                                              @PathVariable long id) {
        return Result.ok(systemService.getRoleMenus(username, id));
    }

    /**
     * 更新角色的菜单权限。
     *
     * @param username 当前登录用户名
     * @param id      角色 ID
     * @param req     菜单 ID 列表
     * @return 更新结果
     */
    @PutMapping("/roles/{id}/menus")
    public Result<Map<String, Object>> updateRoleMenus(@AuthenticationPrincipal String username,
                                                       @PathVariable long id,
                                                       @Valid @RequestBody UpdateRoleMenusRequest req) {
        systemService.updateRoleMenus(username, id, req);
        return Result.ok(Map.of("id", id, "updated", true));
    }

    // -------- 菜单管理 --------

    /**
     * 获取菜单列表（树形结构）。
     *
     * @param username 当前登录用户名
     * @return 菜单列表
     */
    @GetMapping("/menus")
    public Result<List<MenuResponse>> menus(@AuthenticationPrincipal String username) {
        return Result.ok(systemService.listMenus(username));
    }

    /**
     * 获取当前用户的菜单权限 ID 列表。
     *
     * @param username 当前登录用户名
     * @return 菜单 ID 列表
     */
    @GetMapping("/my-menus")
    public Result<UserMenuResponse> myMenus(@AuthenticationPrincipal String username) {
        return Result.ok(systemService.listUserMenuIds(username));
    }

    /**
     * 创建新菜单。
     *
     * @param username 当前登录用户名
     * @param req     菜单信息
     * @return 创建的菜单信息
     */
    @PostMapping("/menus")
    public Result<MenuResponse> createMenu(@AuthenticationPrincipal String username,
                                            @Valid @RequestBody CreateMenuRequest req) {
        return Result.ok(systemService.createMenu(username, req));
    }

    /**
     * 更新菜单信息。
     *
     * @param username 当前登录用户名
     * @param id      菜单 ID
     * @param req     更新内容
     * @return 更新后的菜单信息
     */
    @PutMapping("/menus/{id}")
    public Result<MenuResponse> updateMenu(@AuthenticationPrincipal String username,
                                           @PathVariable long id,
                                           @Valid @RequestBody UpdateMenuRequest req) {
        return Result.ok(systemService.updateMenu(username, id, req));
    }

    /**
     * 删除菜单。
     *
     * @param username 当前登录用户名
     * @param id      菜单 ID
     * @return 删除结果
     */
    @DeleteMapping("/menus/{id}")
    public Result<Map<String, Object>> deleteMenu(@AuthenticationPrincipal String username,
                                                  @PathVariable long id) {
        systemService.deleteMenu(username, id);
        return Result.ok(Map.of("id", id, "deleted", true));
    }
}
