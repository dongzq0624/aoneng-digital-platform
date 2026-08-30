package com.example.rag.controller;

import com.example.rag.service.PlatformRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

import java.util.*;

@RestController
@RequestMapping("/api/system")
public class SystemController {
    private final PlatformRepository repo;

    public SystemController(PlatformRepository repo) {
        this.repo = repo;
    }

    @GetMapping("/users")
    public Map<String, Object> users() {
        List<Map<String, Object>> items = repo.users();
        return Map.of("items", items, "total", items.size());
    }

    @GetMapping("/users/{id}")
    public Map<String, Object> user(@PathVariable long id) {
        return repo.users().stream().filter(x -> Objects.equals(((Number) x.get("id")).longValue(), id)).findFirst().orElseThrow();
    }

    @PostMapping("/users")
    public Map<String, Object> createUser(@RequestBody Map<String, Object> req) {
        try { return repo.createUser(req); }
        catch (IllegalArgumentException e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e); }
    }

    @PutMapping("/users/{id}")
    public Map<String, Object> updateUser(@PathVariable long id, @RequestBody Map<String, Object> req) {
        try { return repo.updateUser(id, req); }
        catch (IllegalArgumentException e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e); }
    }

    @DeleteMapping("/users/{id}")
    public Map<String, Object> deleteUser(@PathVariable long id) {
        repo.deleteUser(id);
        return Map.of("id", id, "deleted", true);
    }

    @PostMapping("/users/{id}/reset-password")
    public Map<String, Object> resetPassword(@PathVariable long id) {
        repo.resetPassword(id);
        return Map.of("id", id, "reset", true);
    }

    @GetMapping("/depts")
    public List<Map<String, Object>> depts() {
        return repo.departments();
    }

    @PostMapping("/depts")
    public Map<String, Object> createDept(@RequestBody Map<String, Object> req) {
        try { return repo.createDepartment(req); }
        catch (IllegalArgumentException e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e); }
    }

    @PutMapping("/depts/{id}")
    public Map<String, Object> updateDept(@PathVariable long id, @RequestBody Map<String, Object> req) {
        try { return repo.updateDepartment(id, req); }
        catch (IllegalArgumentException e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e); }
    }

    @DeleteMapping("/depts/{id}")
    public Map<String, Object> deleteDept(@PathVariable long id) {
        try { repo.deleteDepartment(id); }
        catch (IllegalStateException e) { throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage(), e); }
        return Map.of("id", id, "deleted", true);
    }

    @GetMapping("/roles")
    public List<Map<String, Object>> roles(@AuthenticationPrincipal String username) {
        requireAdmin(username);
        return repo.roles();
    }

    @PostMapping("/roles")
    public Map<String, Object> createRole(@AuthenticationPrincipal String username, @RequestBody Map<String, Object> req) {
        requireAdmin(username);
        try { return repo.createRole(req); }
        catch (IllegalArgumentException e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e); }
    }

    @PutMapping("/roles/{id}")
    public Map<String, Object> updateRole(@AuthenticationPrincipal String username, @PathVariable long id, @RequestBody Map<String, Object> req) {
        requireAdmin(username);
        try { return repo.updateRole(id, req); }
        catch (IllegalArgumentException e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e); }
    }

    @DeleteMapping("/roles/{id}")
    public Map<String, Object> deleteRole(@AuthenticationPrincipal String username, @PathVariable long id) {
        requireAdmin(username);
        try { repo.deleteRole(id); }
        catch (IllegalArgumentException e) { throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage(), e); }
        catch (IllegalStateException e) { throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage(), e); }
        return Map.of("id", id, "deleted", true);
    }

    @GetMapping("/roles/{id}/menus")
    public Map<String, Object> roleMenus(@AuthenticationPrincipal String username, @PathVariable long id) {
        requireAdmin(username);
        return Map.of("menuIds", repo.roleMenuIds(id));
    }

    @PutMapping("/roles/{id}/menus")
    public Map<String, Object> updateRoleMenus(@AuthenticationPrincipal String username, @PathVariable long id, @RequestBody Map<String, Object> req) {
        requireAdmin(username);
        try { repo.updateRoleMenus(id, req); }
        catch (IllegalArgumentException e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e); }
        return Map.of("id", id, "updated", true);
    }

    @GetMapping("/menus")
    public List<Map<String, Object>> menus(@AuthenticationPrincipal String username) {
        requireAdmin(username);
        return repo.menus();
    }

    @GetMapping("/my-menus")
    public Map<String, Object> myMenus(@AuthenticationPrincipal String username) {
        return Map.of("menuIds", repo.userMenuIds(username));
    }

    @PostMapping("/menus")
    public Map<String, Object> createMenu(@AuthenticationPrincipal String username, @RequestBody Map<String, Object> req) {
        requireAdmin(username);
        try { return repo.createMenu(req); }
        catch (IllegalArgumentException e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e); }
    }

    @PutMapping("/menus/{id}")
    public Map<String, Object> updateMenu(@AuthenticationPrincipal String username, @PathVariable long id, @RequestBody Map<String, Object> req) {
        requireAdmin(username);
        try { return repo.updateMenu(id, req); }
        catch (IllegalArgumentException e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e); }
    }

    @DeleteMapping("/menus/{id}")
    public Map<String, Object> deleteMenu(@AuthenticationPrincipal String username, @PathVariable long id) {
        requireAdmin(username);
        try { repo.deleteMenu(id); }
        catch (IllegalArgumentException e) { throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage(), e); }
        catch (IllegalStateException e) { throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage(), e); }
        return Map.of("id", id, "deleted", true);
    }

    private void requireAdmin(String username) {
        if (!"admin".equals(username)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "仅系统管理员可管理角色和菜单");
    }
}
