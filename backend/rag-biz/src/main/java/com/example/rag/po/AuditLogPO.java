package com.example.rag.po;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 审计日志表 {@code audit_log} 的持久化对象。
 */
@Data
@TableName("audit_log")
public class AuditLogPO {

    /** 审计日志主键 ID（输入式，不自动生成）。 */
    @TableId(type = IdType.INPUT)
    private Long id;

    /** 操作人用户 ID。 */
    @TableField("user_id")
    private Long userId;

    /** 操作人用户名（冗余存储，便于查询）。 */
    private String username;

    /** 操作行为（如登录、创建、上传、删除）。 */
    private String action;

    /** 所属模块（auth、kb、rag、system 等）。 */
    private String module;

    /** 操作对象类型（如 KnowledgeBase、Document）。 */
    @TableField("object_type")
    private String objectType;

    /** 操作对象 ID。 */
    @TableField("object_id")
    private Long objectId;

    /** 详细上下文（JSON 字符串）。 */
    private String detail;

    /** 操作者 IP 地址。 */
    private String ip;

    /** 操作者 User-Agent。 */
    @TableField("user_agent")
    private String userAgent;

    /** 操作结果：1=成功，0=失败。 */
    private Integer result;

    /** 操作发生时间，由数据库自动维护。 */
    @TableField(value = "created_at", insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private OffsetDateTime createdAt;
}
