package com.example.rag.po;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 角色表 {@code sys_role} 的持久化对象。
 */
@Data
@TableName("sys_role")
public class SysRolePO {

    /** 角色主键 ID（输入式，不自动生成）。 */
    @TableId(type = IdType.INPUT)
    private Long id;

    /** 角色编码（全大写字母、数字、下划线，全局唯一）。 */
    private String code;

    /** 角色名称。 */
    private String name;

    /** 备注说明。 */
    private String remark;

    /** 状态：1=启用，0=停用。 */
    private Integer status;

    /** 创建时间，由数据库自动维护。 */
    @TableField(value = "created_at", insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private OffsetDateTime createdAt;
}
