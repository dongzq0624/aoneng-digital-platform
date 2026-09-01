package com.example.rag.po;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 部门表 {@code sys_dept} 的持久化对象。
 */
@Data
@TableName("sys_dept")
public class SysDeptPO {

    /** 部门主键 ID（输入式，不自动生成）。 */
    @TableId(type = IdType.INPUT)
    private Long id;

    /** 上级部门 ID（顶级部门为 0 或 null）。 */
    @TableField("parent_id")
    private Long parentId;

    /** 部门名称。 */
    private String name;

    /** 祖先路径（如 "/1/3/"）。 */
    private String ancestors;

    /** 显示排序（数值越小越靠前）。 */
    private Integer sort;

    /** 状态：1=启用，0=停用。 */
    private Integer status;

    /** 创建时间，由数据库自动维护。 */
    @TableField(value = "created_at", insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private OffsetDateTime createdAt;
}
