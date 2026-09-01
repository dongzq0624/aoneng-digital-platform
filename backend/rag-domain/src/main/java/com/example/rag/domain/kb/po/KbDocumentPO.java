package com.example.rag.domain.kb.po;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 知识库文档表 {@code kb_document} 的持久化对象。
 */
@Data
@TableName("kb_document")
public class KbDocumentPO {

    /** 文档主键 ID（输入式，不自动生成）。 */
    @TableId(type = IdType.INPUT)
    private Long id;

    /** 所属知识库 ID。 */
    @TableField("kb_id")
    private Long kbId;

    /** 文件原始名称。 */
    @TableField("file_name")
    private String fileName;

    /** 文件扩展名（如 pdf、docx）。 */
    @TableField("file_type")
    private String fileType;

    /** 文件大小（字节）。 */
    @TableField("file_size")
    private Long fileSize;

    /** MinIO 对象键。 */
    @TableField("object_key")
    private String objectKey;

    /** 文档版本号（重新上传时递增）。 */
    private Integer version;

    /** 解析状态：PENDING / PARSING / SUCCESS / FAILED。 */
    @TableField("parse_status")
    private String parseStatus;

    /** 分块状态：PENDING / INDEXING / INDEXED / FAILED。 */
    @TableField("chunk_status")
    private String chunkStatus;

    /** 已生成的分块数量。 */
    @TableField("chunk_count")
    private Integer chunkCount;

    /** 解析或索引错误信息（处理失败时填写）。 */
    @TableField("error_msg")
    private String errorMsg;

    /** 上传者用户 ID。 */
    @TableField("uploader_id")
    private Long uploaderId;

    /** 上传时间，由数据库自动维护。 */
    @TableField(value = "created_at", insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private OffsetDateTime createdAt;

    /** 最近更新时间，由数据库自动维护。 */
    @TableField(value = "updated_at", insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private OffsetDateTime updatedAt;

    /** 逻辑删除标记。 */
    @TableField(value = "deleted", insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private Boolean deleted;
}
