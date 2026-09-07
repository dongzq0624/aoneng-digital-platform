package com.aoneng.rag.audit.service.impl;

import com.aoneng.rag.audit.convert.AuditConvert;
import com.aoneng.rag.audit.service.AuditPersistenceService;
import com.aoneng.rag.audit.service.AuditService;
import com.aoneng.rag.audit.vo.AuditLogListVO;
import com.aoneng.rag.audit.vo.AuditLogVO;
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

    private final AuditPersistenceService repository;
    private final ObjectMapper objectMapper;

    public AuditServiceImpl(AuditPersistenceService repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Override
    public AuditLogListVO listRecentLogs(String keyword, String module, String action, int page, int pageSize) {
        List<AuditPersistenceService.AuditLogRow> all = repository.findRecentLogs();
        String normalizedKeyword = keyword == null ? "" : keyword.trim().toLowerCase();
        String normalizedModule = module == null ? "" : module.trim();
        String normalizedAction = action == null ? "" : action.trim();
        List<AuditPersistenceService.AuditLogRow> filtered = all.stream().filter(row ->
                (normalizedKeyword.isBlank() || (String.valueOf(row.username()) + row.action() + row.module() + row.detailJson())
                        .toLowerCase().contains(normalizedKeyword))
                        && (normalizedModule.isBlank() || normalizedModule.equals(row.module()))
                        && (normalizedAction.isBlank() || normalizedAction.equals(row.action()))
        ).toList();
        int safePage = Math.max(1, page);
        int safeSize = Math.max(1, Math.min(pageSize, 100));
        int from = Math.min((safePage - 1) * safeSize, filtered.size());
        int to = Math.min(from + safeSize, filtered.size());
        List<AuditPersistenceService.AuditLogRow> rows = filtered.subList(from, to);
        List<AuditLogVO> items = AuditConvert.INSTANCE.toResponses(rows, objectMapper);
        return new AuditLogListVO(items, filtered.size());
    }
}
