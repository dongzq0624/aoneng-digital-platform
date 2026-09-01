package com.aoneng.rag.system.controller;

import com.aoneng.rag.common.result.Result;
import com.aoneng.rag.system.dto.CreateDepartmentDTO;
import com.aoneng.rag.system.dto.CreateMenuDTO;
import com.aoneng.rag.system.dto.CreateRoleDTO;
import com.aoneng.rag.system.dto.CreateUserDTO;
import com.aoneng.rag.system.dto.UpdateDepartmentDTO;
import com.aoneng.rag.system.dto.UpdateMenuDTO;
import com.aoneng.rag.system.dto.UpdateRoleMenusDTO;
import com.aoneng.rag.system.dto.UpdateRoleDTO;
import com.aoneng.rag.system.dto.UpdateUserDTO;
import com.aoneng.rag.system.service.SystemService;
import com.aoneng.rag.system.vo.DepartmentVO;
import com.aoneng.rag.system.vo.MenuVO;
import com.aoneng.rag.system.vo.OperationResultVO;
import com.aoneng.rag.system.vo.RoleMenuVO;
import com.aoneng.rag.system.vo.RoleVO;
import com.aoneng.rag.system.vo.UserMenuVO;
import com.aoneng.rag.system.vo.UserVO;
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

/**
 * 系统管理控制器。负责 HTTP 协议层面的请求校验和响应封装，
 * 所有用户、部门、角色、菜单等管理业务逻辑委托给 {@link SystemService}。
 */
@RestController
@RequestMapping("/api/v1/system")
public class SystemController {

    private final SystemService systemService;

    public SystemController(SystemService systemService) {
        this.systemService = systemService;
    }

    // -------- 用户管理 --------

    @GetMapping("/users")
    public Result<List<UserVO>> users() {
        return Result.ok(systemService.listUsers());
    }

    @GetMapping("/users/{id}")
    public Result<UserVO> user(@PathVariable long id) {
        return Result.ok(systemService.getUser(id));
    }

    @PostMapping("/users")
    public Result<UserVO> createUser(@Valid @RequestBody CreateUserDTO req) {
        return Result.ok(systemService.createUser(req));
    }

    @PutMapping("/users/{id}")
    public Result<UserVO> updateUser(@PathVariable long id,
                                      @Valid @RequestBody UpdateUserDTO req) {
        return Result.ok(systemService.updateUser(id, req));
    }

    @DeleteMapping("/users/{id}")
    public Result<OperationResultVO> deleteUser(@PathVariable long id) {
        systemService.deleteUser(id);
        return Result.ok(new OperationResultVO(id, "deleted"));
    }

    @PostMapping("/users/{id}/reset-password")
    public Result<OperationResultVO> resetPassword(@PathVariable long id) {
        systemService.resetPassword(id);
        return Result.ok(new OperationResultVO(id, "reset"));
    }

    // -------- 部门管理 --------

    @GetMapping("/depts")
    public Result<List<DepartmentVO>> depts() {
        return Result.ok(systemService.listDepartments());
    }

    @PostMapping("/depts")
    public Result<DepartmentVO> createDept(@Valid @RequestBody CreateDepartmentDTO req) {
        return Result.ok(systemService.createDepartment(req));
    }

    @PutMapping("/depts/{id}")
    public Result<DepartmentVO> updateDept(@PathVariable long id,
                                           @Valid @RequestBody UpdateDepartmentDTO req) {
        return Result.ok(systemService.updateDepartment(id, req));
    }

    @DeleteMapping("/depts/{id}")
    public Result<OperationResultVO> deleteDept(@PathVariable long id) {
        systemService.deleteDepartment(id);
        return Result.ok(new OperationResultVO(id, "deleted"));
    }

    // -------- 角色管理 --------

    @GetMapping("/roles")
    public Result<List<RoleVO>> roles(@AuthenticationPrincipal String username) {
        return Result.ok(systemService.listRoles(username));
    }

    @PostMapping("/roles")
    public Result<RoleVO> createRole(@AuthenticationPrincipal String username,
                                     @Valid @RequestBody CreateRoleDTO req) {
        return Result.ok(systemService.createRole(username, req));
    }

    @PutMapping("/roles/{id}")
    public Result<RoleVO> updateRole(@AuthenticationPrincipal String username,
                                     @PathVariable long id,
                                     @Valid @RequestBody UpdateRoleDTO req) {
        return Result.ok(systemService.updateRole(username, id, req));
    }

    @DeleteMapping("/roles/{id}")
    public Result<OperationResultVO> deleteRole(@AuthenticationPrincipal String username,
                                                 @PathVariable long id) {
        systemService.deleteRole(username, id);
        return Result.ok(new OperationResultVO(id, "deleted"));
    }

    @GetMapping("/roles/{id}/menus")
    public Result<RoleMenuVO> roleMenus(@AuthenticationPrincipal String username,
                                        @PathVariable long id) {
        return Result.ok(systemService.getRoleMenus(username, id));
    }

    @PutMapping("/roles/{id}/menus")
    public Result<OperationResultVO> updateRoleMenus(@AuthenticationPrincipal String username,
                                                      @PathVariable long id,
                                                      @Valid @RequestBody UpdateRoleMenusDTO req) {
        systemService.updateRoleMenus(username, id, req);
        return Result.ok(new OperationResultVO(id, "updated"));
    }

    // -------- 菜单管理 --------

    @GetMapping("/menus")
    public Result<List<MenuVO>> menus(@AuthenticationPrincipal String username) {
        return Result.ok(systemService.listMenus(username));
    }

    @GetMapping("/my-menus")
    public Result<UserMenuVO> myMenus(@AuthenticationPrincipal String username) {
        return Result.ok(systemService.listUserMenuIds(username));
    }

    @PostMapping("/menus")
    public Result<MenuVO> createMenu(@AuthenticationPrincipal String username,
                                     @Valid @RequestBody CreateMenuDTO req) {
        return Result.ok(systemService.createMenu(username, req));
    }

    @PutMapping("/menus/{id}")
    public Result<MenuVO> updateMenu(@AuthenticationPrincipal String username,
                                     @PathVariable long id,
                                     @Valid @RequestBody UpdateMenuDTO req) {
        return Result.ok(systemService.updateMenu(username, id, req));
    }

    @DeleteMapping("/menus/{id}")
    public Result<OperationResultVO> deleteMenu(@AuthenticationPrincipal String username,
                                                 @PathVariable long id) {
        systemService.deleteMenu(username, id);
        return Result.ok(new OperationResultVO(id, "deleted"));
    }
}
