package com.aoneng.rag.audit.service;

import com.aoneng.rag.domain.audit.repository.AuditRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

/** 审计日志持久化门面，隔离审计领域与其他应用服务。 */
@Service
public class AuditPersistenceService {

    private static final Logger log = LoggerFactory.getLogger(AuditPersistenceService.class);

    private final AuditRepository repository;
    private final ObjectMapper objectMapper;

    public AuditPersistenceService(AuditRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    public List<AuditLogRow> findRecentLogs() {
        return repository.findRecentLogs().stream().map(row -> {
            Object createdAt = row.get("createdAt");
            OffsetDateTime created = createdAt == null ? null
                    : (createdAt instanceof OffsetDateTime odt ? odt
                    : (createdAt instanceof Timestamp ts ? ts.toInstant().atOffset(ZoneOffset.UTC) : null));
            return new AuditLogRow(number(row.get("id")), created,
                    string(row.get("username")), string(row.get("action")), string(row.get("module")),
                    string(row.get("detailJson")), (int) number(row.get("result")));
        }).toList();
    }

    /** Persist a business audit event without allowing audit-store failures to affect the request. */
    public void record(Long userId, String username, String action, String module, Map<String, Object> detail, int result) {
        String detailJson;
        try {
            detailJson = objectMapper.writeValueAsString(detail == null ? Map.of() : detail);
        } catch (Exception e) {
            detailJson = "{}";
        }
        try {
            repository.insert(userId, username, action, module, detailJson, result);
        } catch (Exception e) {
            // Audit is a side-effect; a temporary audit store outage must not fail the business request.
            log.warn("Audit log persistence failed: action={}, user={}, error={}",
                    action, username, e.getMessage());
        }
    }

    private static long number(Object value) {
        if (value instanceof Number number) return number.longValue();
        try {
            return value == null ? 0L : Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    private static String string(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    public record AuditLogRow(long id, OffsetDateTime createdAt, String username, String action,
                              String module, String detailJson, int result) {
    }
}
