/**
 * 统一 API 响应结构与业务状态码。
 *
 * <p>所有 Controller 返回 {@link com.example.rag.common.result.Result}，
 * 分页列表使用 {@link com.example.rag.common.result.PageResult}。
 * 状态码定义见 {@link com.example.rag.common.result.ResultCode}。</p>
 *
 * <p>前端应基于响应体中的 {@code code} 字段判断业务成功与否，
 * 而非依赖 HTTP 状态码。</p>
 */
package com.example.rag.common.result;
