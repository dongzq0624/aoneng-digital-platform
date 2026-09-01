package com.aoneng.rag.domain.chat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.aoneng.rag.domain.chat.po.KbChatMessagePO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface KbChatMessageMapper extends BaseMapper<KbChatMessagePO> {

    int insertReturningId(KbChatMessagePO message);

    int nextSequenceNo(@Param("conversationId") long conversationId);

    int updateFailure(@Param("messageId") long messageId,
                      @Param("conversationId") long conversationId,
                      @Param("message") String message);

    int completeAssistantMessage(@Param("messageId") long messageId,
                                 @Param("conversationId") long conversationId,
                                 @Param("content") String content,
                                 @Param("chunkIds") long[] chunkIds,
                                 @Param("modelName") String modelName,
                                 @Param("latencyMs") int latencyMs,
                                 @Param("qaRecordId") long qaRecordId);

    /**
     * Cursor-paginated chat messages (descending sequence_no by default).
     */
    List<Map<String, Object>> selectMessages(@Param("conversationId") long conversationId,
                                             @Param("beforeSequence") Integer beforeSequence,
                                             @Param("limit") int limit);

    List<Map<String, Object>> selectRecentHistory(@Param("conversationId") long conversationId,
                                                  @Param("limit") int limit);
}
