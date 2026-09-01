package com.example.rag.po;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 对话会话表 {@code kb_conversation} 的持久化对象。
 */
@Data
@TableName("kb_conversation")
public class KbConversationPO {

    /** 会话主键 ID（输入式，不自动生成）。 */
    @TableId(type = IdType.INPUT)
    private Long id;

    /** 会话所属用户 ID。 */
    @TableField("user_id")
    private Long userId;

    /** 会话标题。 */
    private String title;

    /** 本次会话选定的知识库 ID 列表（JSON 数组字符串）。 */
    @TableField("selected_kb_ids")
    private String selectedKbIds;

    /** 最近一条消息的时间，用于会话列表排序。 */
    @TableField(value = "last_message_at", insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private OffsetDateTime lastMessageAt;

    /** 创建时间，由数据库自动维护。 */
    @TableField(value = "created_at", insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private OffsetDateTime createdAt;

    /** 最近更新时间，由数据库自动维护。 */
    @TableField(value = "updated_at", insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private OffsetDateTime updatedAt;

    /** 逻辑删除时间，删除会话时由数据库或调用方填充。 */
    @TableField(value = "deleted_at", insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private OffsetDateTime deletedAt;

    /** 旧版 QA 记录 ID（迁移兼容字段）。 */
    @TableField("legacy_qa_record_id")
    private Long legacyQaRecordId;
}
