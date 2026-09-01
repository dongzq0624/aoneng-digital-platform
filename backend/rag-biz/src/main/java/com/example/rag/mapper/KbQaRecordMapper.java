package com.example.rag.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.rag.po.KbQaRecordPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface KbQaRecordMapper extends BaseMapper<KbQaRecordPO> {

    int insertReturningId(KbQaRecordPO record);

    int updateRerankScores(@Param("id") long id, @Param("scores") String scores);

    int updateAssistantMessageId(@Param("id") long id, @Param("messageId") long messageId);

    List<Map<String, Object>> selectRecords(@Param("userId") long userId,
                                            @Param("limit") int limit,
                                            @Param("offset") int offset);

    int countByUser(@Param("userId") long userId);

    Map<String, Object> selectRecord(@Param("id") long id, @Param("userId") long userId);

    int existsById(@Param("id") long id, @Param("userId") long userId);
}
