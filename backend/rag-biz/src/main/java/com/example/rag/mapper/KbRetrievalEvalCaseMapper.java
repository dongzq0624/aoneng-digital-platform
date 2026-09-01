package com.example.rag.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.rag.po.KbRetrievalEvalCasePO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Map;

@Mapper
public interface KbRetrievalEvalCaseMapper extends BaseMapper<KbRetrievalEvalCasePO> {

    List<Map<String, Object>> selectAllCases();

    Map<String, Object> selectCaseById(long id);

    int insertCase(KbRetrievalEvalCasePO caseRow);
}
