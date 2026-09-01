package com.aoneng.rag.infra.repository;

import com.aoneng.rag.domain.audit.mapper.AuditLogMapper;
import com.aoneng.rag.domain.audit.po.AuditLogPO;
import com.aoneng.rag.domain.audit.repository.AuditRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
public class AuditRepositoryImpl implements AuditRepository {
    private final AuditLogMapper mapper;

    public AuditRepositoryImpl(AuditLogMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<Map<String, Object>> findRecentLogs() {
        return mapper.selectRecentLogs();
    }

    @Override
    public void insert(String username, String action, String module, String detailJson, int result) {
        AuditLogPO p = new AuditLogPO();
        p.setUsername(username);
        p.setAction(action);
        p.setModule(module);
        p.setDetail(detailJson);
        p.setResult(result);
        mapper.insertReturningId(p);
    }
}
