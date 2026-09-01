package com.example.rag.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.rag.po.AuditLogPO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Map;

@Mapper
public interface AuditLogMapper extends BaseMapper<AuditLogPO> {

    List<Map<String, Object>> selectRecentLogs();

    int insertReturningId(AuditLogPO log);
}
