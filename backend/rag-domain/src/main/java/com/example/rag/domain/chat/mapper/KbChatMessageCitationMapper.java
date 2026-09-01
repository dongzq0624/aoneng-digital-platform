package com.example.rag.domain.chat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.rag.domain.chat.po.KbChatMessageCitationPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface KbChatMessageCitationMapper extends BaseMapper<KbChatMessageCitationPO> {

    int insertCitation(KbChatMessageCitationPO citation);

    List<KbChatMessageCitationPO> selectByMessage(@Param("messageId") long messageId);
}
