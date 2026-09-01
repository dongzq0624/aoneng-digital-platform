package com.example.rag.domain.audit.repository;

import java.util.List;
import java.util.Map;

/**
 * 审计日志仓储接口（audit bounded context）。
 */
public interface AuditRepository {

    /**
     * 查询最近的审计日志。
     */
    List<Map<String, Object>> findRecentLogs();

    /**
     * 插入审计日志。
     */
    void insert(String username, String action, String module, String detailJson, int result);
}
