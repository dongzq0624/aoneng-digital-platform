package com.aoneng.rag.domain.audit.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.aoneng.rag.domain.audit.po.AuditLogPO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Map;

@Mapper
public interface AuditLogMapper extends BaseMapper<AuditLogPO> {

    List<Map<String, Object>> selectRecentLogs();

    int insertReturningId(AuditLogPO log);
}
