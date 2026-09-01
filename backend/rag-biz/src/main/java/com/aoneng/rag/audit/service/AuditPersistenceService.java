package com.aoneng.rag.audit.service;

import com.aoneng.rag.domain.audit.repository.AuditRepository;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

/** 审计日志持久化门面，隔离审计领域与其他应用服务。 */
@Service
public class AuditPersistenceService {

    private final AuditRepository repository;

    public AuditPersistenceService(AuditRepository repository) {
        this.repository = repository;
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
