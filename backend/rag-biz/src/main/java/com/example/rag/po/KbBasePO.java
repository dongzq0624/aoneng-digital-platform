package com.example.rag.po;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 知识库表 {@code kb_knowledge_base} 的持久化对象。
 */
@Data
@TableName("kb_knowledge_base")
public class KbBasePO {

    /** 知识库主键 ID（输入式，不自动生成）。 */
    @TableId(type = IdType.INPUT)
    private Long id;

    /** 知识库名称。 */
    private String name;

    /** 知识库描述。 */
    private String description;

    /** 知识库分类。 */
    private String category;

    /** 可见性：PUBLIC / DEPARTMENT / PRIVATE。 */
    private String visibility;

    /** 所有者用户 ID。 */
    @TableField("owner_id")
    private Long ownerId;

    /** 所属部门 ID。 */
    @TableField("dept_id")
    private Long deptId;

    /** 文本分块大小（字符数）。 */
    @TableField("chunk_size")
    private Integer chunkSize;

    /** 分块重叠字符数。 */
    @TableField("chunk_overlap")
    private Integer chunkOverlap;

    /** 使用的嵌入模型名称。 */
    @TableField("embedding_model")
    private String embeddingModel;

    /** 状态：1=启用，0=停用。 */
    private Integer status;

    /** 创建时间，由数据库自动维护。 */
    @TableField(value = "created_at", insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private OffsetDateTime createdAt;

    /** 最近更新时间，由数据库自动维护。 */
    @TableField(value = "updated_at", insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private OffsetDateTime updatedAt;

    /** 逻辑删除标记。 */
    @TableField(value = "deleted", insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private Boolean deleted;
}
