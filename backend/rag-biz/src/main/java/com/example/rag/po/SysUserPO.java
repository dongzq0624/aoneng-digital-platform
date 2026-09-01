package com.example.rag.po;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 平台用户表 {@code sys_user} 的持久化对象。
 */
@Data
@TableName("sys_user")
public class SysUserPO {

    /** 用户主键 ID（输入式，不自动生成）。 */
    @TableId(type = IdType.INPUT)
    private Long id;

    /** 工号。 */
    @TableField("employee_no")
    private String employeeNo;

    /** 用户名（登录账号，全局唯一）。 */
    private String username;

    /** 加密后的密码（BCrypt 哈希）。 */
    @TableField("password_hash")
    private String passwordHash;

    /** 真实姓名。 */
    @TableField("real_name")
    private String realName;

    /** 邮箱地址。 */
    private String email;

    /** 电话号码。 */
    private String phone;

    /** 所属部门 ID。 */
    @TableField("dept_id")
    private Long deptId;

    /** 头像 URL（可选）。 */
    private String avatar;

    /** 状态：1=启用，0=停用。 */
    private Integer status;

    /** 最近登录时间。 */
    @TableField("last_login_at")
    private OffsetDateTime lastLoginAt;

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
