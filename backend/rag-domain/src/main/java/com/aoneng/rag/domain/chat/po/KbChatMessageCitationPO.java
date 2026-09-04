package com.aoneng.rag.domain.chat.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 消息引用记录表 {@code kb_chat_message_citation} 的持久化对象。
 * 用于存储助手消息中引用的具体文档分块信息。
 */
@Data
@TableName("kb_chat_message_citation")
public class KbChatMessageCitationPO {

    /** 引用主键 ID（输入式，不自动生成）。 */
    @TableId(type = IdType.INPUT)
    private Long id;

    /** 关联的消息 ID。 */
    @TableField("message_id")
    private Long messageId;

    /** 引用的分块 ID。 */
    @TableField("chunk_id")
    private Long chunkId;

    /** 引用的文档 ID。 */
    @TableField("doc_id")
    private Long docId;

    /** 引用的知识库 ID。 */
    @TableField("kb_id")
    private Long kbId;

    /** 来源文件名。 */
    @TableField("file_name")
    private String fileName;

    /** 引用的分块文本摘要（已脱敏，长度有限）。 */
    private String snippet;

    /** 在答案中的引用顺序（从 1 开始）。 */
    @TableField("rank_no")
    private Integer rankNo;

    /** 相关性评分。 */
    private Double score;
}
