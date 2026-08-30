package com.example.rag.controller;

import com.example.rag.service.PlatformRepository;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/audit")
public class AuditController {
    private final PlatformRepository repo;

    public AuditController(PlatformRepository repo) {
        this.repo = repo;
    }

    @GetMapping("/logs")
    public Map<String, Object> logs() {
        List<Map<String, Object>> items = repo.auditLogs();
        return Map.of("items", items, "total", items.size());
    }
}
//给后端代码添加必要的注释，同时将添加注释添加到AGENTS