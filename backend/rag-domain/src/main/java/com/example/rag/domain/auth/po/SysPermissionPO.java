package com.example.rag.domain.auth.po;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 权限菜单表 {@code sys_permission} 的持久化对象。
 */
@Data
@TableName("sys_permission")
public class SysPermissionPO {

    /** 菜单/权限主键 ID（输入式，不自动生成）。 */
    @TableId(type = IdType.INPUT)
    private Long id;

    /** 上级菜单 ID（顶级菜单为 0）。 */
    @TableField("parent_id")
    private Long parentId;

    /** 菜单或按钮名称。 */
    private String name;

    /** 权限标识（如 kb:create）。 */
    private String perms;

    /** 类型：1=目录，2=菜单，3=按钮。 */
    private Integer type;

    /** 路由路径（仅菜单类型使用）。 */
    private String path;

    /** 显示排序（数值越小越靠前）。 */
    private Integer sort;

    /** 状态：1=启用，0=停用。 */
    private Integer status;

    /** 创建时间，由数据库自动维护。 */
    @TableField(value = "created_at", insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private OffsetDateTime createdAt;
}
