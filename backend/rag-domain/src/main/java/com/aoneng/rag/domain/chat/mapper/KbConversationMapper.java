package com.aoneng.rag.domain.chat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.aoneng.rag.domain.chat.po.KbConversationPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface KbConversationMapper extends BaseMapper<KbConversationPO> {

    /**
     * Cursor-paginated conversations for a user.
     */
    List<Map<String, Object>> selectConversations(@Param("userId") long userId,
                                                  @Param("cursorTime") java.sql.Timestamp cursorTime,
                                                  @Param("cursorId") Long cursorId,
                                                  @Param("limit") int limit);

    Map<String, Object> selectConversation(@Param("id") long id, @Param("userId") long userId);

    int insertReturningId(KbConversationPO conversation);

    int updateTitle(@Param("id") long id,
                    @Param("userId") long userId,
                    @Param("title") String title);

    int softDelete(@Param("id") long id, @Param("userId") long userId);

    Long lockConversation(@Param("id") long id, @Param("userId") long userId);

    int updateSelectedKbIds(@Param("id") long id, @Param("selectedKbIds") long[] selectedKbIds);

    int touchLastMessage(@Param("id") long id);
}
