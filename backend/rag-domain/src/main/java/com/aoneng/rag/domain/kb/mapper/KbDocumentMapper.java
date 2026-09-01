package com.aoneng.rag.domain.kb.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.aoneng.rag.domain.kb.po.KbDocumentPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface KbDocumentMapper extends BaseMapper<KbDocumentPO> {

    Map<String, Object> selectDocument(@Param("id") long id);

    List<Map<String, Object>> selectDocumentsByKb(@Param("kbId") long kbId);

    int insertReturningId(KbDocumentPO doc);

    int updateStatus(@Param("id") long id,
                     @Param("parseStatus") String parseStatus,
                     @Param("chunkStatus") String chunkStatus,
                     @Param("chunkCount") int chunkCount,
                     @Param("errorMsg") String errorMsg);

    int softDelete(@Param("id") long id);
}
