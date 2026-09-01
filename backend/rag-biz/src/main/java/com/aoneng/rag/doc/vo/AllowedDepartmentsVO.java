package com.aoneng.rag.doc.vo;

import java.util.List;

/**
 * 知识库允许访问的部门响应体。
 *
 * @param departmentIds 允许访问该知识库的部门 ID 列表
 */
public record AllowedDepartmentsVO(List<Long> departmentIds) {
}
