package com.example.rag.controller;

import com.example.rag.security.JwtUtil;
import com.example.rag.service.PlatformRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final JwtUtil jwtUtil;
    private final PlatformRepository repo;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AuthController(JwtUtil jwtUtil, PlatformRepository repo) {
        this.jwtUtil = jwtUtil;
        this.repo = repo;
    }

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody Map<String, String> body) {
        String username = Optional.ofNullable(body.get("username")).orElse("").trim();
        String password = Optional.ofNullable(body.get("password")).orElse("");
        Map<String, Object> user = repo.userByUsername(username).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户名或密码错误"));
        String hash = repo.passwordHash(username).orElse("");
        if (hash.isBlank() || !passwordEncoder.matches(password, hash))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户名或密码错误");
        if (user.get("status") instanceof Number status && status.intValue() == 0)
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "用户已停用");
        return Map.of("token", jwtUtil.issue(((Number) user.get("id")).longValue(), username), "user", Map.of("id", user.get("id"), "username", username, "realName", user.get("realName"), "deptId", user.get("deptId")));
    }

    @GetMapping("/me")
    public Map<String, Object> me(@org.springframework.security.core.annotation.AuthenticationPrincipal String username) {
        return repo.userByUsername(username).map(user -> Map.of("id", user.get("id"), "username", user.get("username"), "realName", user.get("realName"), "deptId", user.get("deptId"))).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }
}
