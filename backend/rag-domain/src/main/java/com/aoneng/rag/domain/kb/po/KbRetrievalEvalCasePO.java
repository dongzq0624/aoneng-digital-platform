package com.aoneng.rag.domain.kb.po;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 检索评测用例表 {@code kb_retrieval_eval_case} 的持久化对象。
 */
@Data
@TableName("kb_retrieval_eval_case")
public class KbRetrievalEvalCasePO {

    /** 评测用例主键 ID（输入式，不自动生成）。 */
    @TableId(type = IdType.INPUT)
    private Long id;

    /** 评测查询问题。 */
    private String question;

    /** 期望命中的分块 ID 列表（JSON 数组字符串）。 */
    @TableField("expected_chunk_ids")
    private String expectedChunkIds;

    /** 人工确认的参考答案，用于 RAGAS context recall。 */
    @TableField("reference_answer")
    private String referenceAnswer;

    /** 是否启用该评测用例。 */
    private Boolean enabled;

    /** 备注说明。 */
    private String note;

    /** 创建时间，由数据库自动维护。 */
    @TableField(value = "created_at", insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private OffsetDateTime createdAt;

    /** 最近更新时间，由数据库自动维护。 */
    @TableField(value = "updated_at", insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private OffsetDateTime updatedAt;
}
