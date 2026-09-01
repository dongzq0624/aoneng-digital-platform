package com.example.rag.system.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * 更新用户请求体。所有字段可选，null 值会被忽略。
 *
 * @param realName 真实姓名（可选，最长 64 字符）
 * @param email    邮箱（可选，需符合邮箱格式）
 * @param phone    电话号码（可选，6-20 位数字、加号、减号、空格）
 * @param deptId   所属部门 ID（可选，需为正数）
 * @param status   启用状态（可选，1=启用，0=停用）
 */
public record UpdateUserRequest(
        @Size(max = 64, message = "姓名长度不能超过 64 个字符")
        String realName,

        @Email(message = "邮箱格式不正确")
        String email,

        @Pattern(regexp = "^$|^[0-9+\\- ]{6,20}$", message = "电话格式不正确")
        String phone,

        @Positive(message = "部门 ID 必须为正数")
        Long deptId,

        Integer status) {
}
