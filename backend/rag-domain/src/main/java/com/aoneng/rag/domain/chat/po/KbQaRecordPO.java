package com.aoneng.rag.domain.chat.po;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 问答记录表 {@code kb_qa_record} 的持久化对象。
 */
@Data
@TableName("kb_qa_record")
public class KbQaRecordPO {

    /** 问答记录主键 ID（输入式，不自动生成）。 */
    @TableId(type = IdType.INPUT)
    private Long id;

    /** 提问用户 ID。 */
    @TableField("user_id")
    private Long userId;

    /** 本次问答使用的知识库 ID 列表（JSON 数组字符串）。 */
    @TableField("kb_ids")
    private String kbIds;

    /** 用户问题。 */
    private String question;

    /** 模型回答。 */
    private String answer;

    /** 检索到的分块 ID 列表（JSON 数组字符串）。 */
    @TableField("retrieved_chunk_ids")
    private String retrievedChunkIds;

    /** 重排分数（JSON 数组字符串）。 */
    @TableField("rerank_scores")
    private String rerankScores;

    /** 使用的模型名称。 */
    @TableField("model_name")
    private String modelName;

    /** 提示词版本号。 */
    @TableField("prompt_version")
    private String promptVersion;

    /** 总 token 消耗。 */
    @TableField("total_tokens")
    private Integer totalTokens;

    /** 模型调用耗时（毫秒）。 */
    @TableField("latency_ms")
    private Integer latencyMs;

    /** 用户反馈：1=点赞，-1=点踩，0=未反馈。 */
    private Integer feedback;

    /** 关联会话 ID。 */
    @TableField("conversation_id")
    private Long conversationId;

    /** 关联助手消息 ID。 */
    @TableField("assistant_message_id")
    private Long assistantMessageId;

    /** 创建时间，由数据库自动维护。 */
    @TableField(value = "created_at", insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private OffsetDateTime createdAt;
}
