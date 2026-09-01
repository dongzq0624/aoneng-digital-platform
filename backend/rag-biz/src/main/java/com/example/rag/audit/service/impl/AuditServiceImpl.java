package com.example.rag.audit.service.impl;

import com.example.rag.audit.dto.AuditLogListResponse;
import com.example.rag.audit.dto.AuditLogResponse;
import com.example.rag.audit.service.AuditService;
import com.example.rag.convert.AuditConvert;
import com.example.rag.service.PlatformRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 审计日志服务默认实现。
 * 从 {@link PlatformRepository} 获取最近的审计日志行，
 * 并通过 {@link AuditConvert} 将其映射为 DTO。
 */
@Service
public class AuditServiceImpl implements AuditService {

    private final PlatformRepository repository;
    private final ObjectMapper objectMapper;

    public AuditServiceImpl(PlatformRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    /**
     * 获取最近的审计日志列表。
     *
     * @return 审计日志列表响应
     */
    @Override
    public AuditLogListResponse listRecentLogs() {
        List<PlatformRepository.AuditLogRow> rows = repository.auditLogs();
        List<AuditLogResponse> items = AuditConvert.INSTANCE.toResponses(rows, objectMapper);
        return new AuditLogListResponse(items, items.size());
    }
}
