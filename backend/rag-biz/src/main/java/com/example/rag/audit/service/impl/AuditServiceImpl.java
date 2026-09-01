package com.example.rag.audit.service.impl;

import com.example.rag.audit.convert.AuditConvert;
import com.example.rag.audit.service.AuditService;
import com.example.rag.audit.vo.AuditLogListVO;
import com.example.rag.audit.vo.AuditLogVO;
import com.example.rag.service.PlatformRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 审计日志服务默认实现。
 * 从 {@link PlatformRepository} 获取最近的审计日志行，
 * 并通过 {@link AuditConvert} 将其映射为 VO。
 */
@Service
public class AuditServiceImpl implements AuditService {

    private final PlatformRepository repository;
    private final ObjectMapper objectMapper;

    public AuditServiceImpl(PlatformRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Override
    public AuditLogListVO listRecentLogs() {
        List<PlatformRepository.AuditLogRow> rows = repository.auditLogs();
        List<AuditLogVO> items = AuditConvert.INSTANCE.toResponses(rows, objectMapper);
        return new AuditLogListVO(items, items.size());
    }
}
