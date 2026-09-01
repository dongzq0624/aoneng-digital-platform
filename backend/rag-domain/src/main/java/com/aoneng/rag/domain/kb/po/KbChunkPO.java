package com.aoneng.rag.domain.kb.po;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 知识库文档分块表 {@code kb_chunk} 的持久化对象。
 */
@Data
@TableName("kb_chunk")
public class KbChunkPO {

    /** 分块主键 ID（输入式，与 Qdrant point id 保持稳定映射）。 */
    @TableId(type = IdType.INPUT)
    private Long id;

    /** 所属文档 ID。 */
    @TableField("doc_id")
    private Long docId;

    /** 所属知识库 ID。 */
    @TableField("kb_id")
    private Long kbId;

    /** 在所属文档中的分块序号（从 0 开始）。 */
    private Integer seq;

    /** 分块文本内容。 */
    private String content;

    /** 来源页码（PDF 解析时填写，其他文档为 null）。 */
    @TableField("page_no")
    private Integer pageNo;

    /** 分块的 token 数（可选统计字段）。 */
    @TableField("token_count")
    private Integer tokenCount;

    /** 附加元数据 JSON 字符串。 */
    private String metadata;

    /** 嵌入向量 ID（与向量库中的 point id 一致）。 */
    @TableField("embedding_id")
    private String embeddingId;

    /** 创建时间，由数据库自动维护。 */
    @TableField(value = "created_at", insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private OffsetDateTime createdAt;
}
