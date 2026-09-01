package com.example.rag.domain.chat.po;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 会话消息表 {@code kb_chat_message} 的持久化对象。
 */
@Data
@TableName("kb_chat_message")
public class KbChatMessagePO {

    /** 消息主键 ID（输入式，不自动生成）。 */
    @TableId(type = IdType.INPUT)
    private Long id;

    /** 所属会话 ID。 */
    @TableField("conversation_id")
    private Long conversationId;

    /** 会话内的消息序号（从 1 开始递增）。 */
    @TableField("sequence_no")
    private Integer sequenceNo;

    /** 消息角色：user / assistant / system。 */
    private String role;

    /** 消息文本内容。 */
    private String content;

    /** 消息状态：PENDING / STREAMING / DONE / FAILED。 */
    private String status;

    /** 本轮使用的知识库 ID 列表（JSON 数组字符串）。 */
    @TableField("source_kb_ids")
    private String sourceKbIds;

    /** 检索到的分块 ID 列表（JSON 数组字符串）。 */
    @TableField("retrieved_chunk_ids")
    private String retrievedChunkIds;

    /** 使用的模型名称。 */
    @TableField("model_name")
    private String modelName;

    /** 模型调用耗时（毫秒）。 */
    @TableField("latency_ms")
    private Integer latencyMs;

    /** 错误信息（消息失败时填写）。 */
    @TableField("error_message")
    private String errorMessage;

    /** 关联的 QA 记录 ID。 */
    @TableField("qa_record_id")
    private Long qaRecordId;

    /** 创建时间，由数据库自动维护。 */
    @TableField(value = "created_at", insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private OffsetDateTime createdAt;

    /** 消息完成时间，由数据库自动维护。 */
    @TableField(value = "completed_at", insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private OffsetDateTime completedAt;
}
