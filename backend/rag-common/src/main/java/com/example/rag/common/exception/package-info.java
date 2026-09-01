/**
 * 全局异常类及错误响应结构。
 *
 * <p>所有业务异常均继承自 {@link RuntimeException}，由
 * {@code GlobalExceptionHandler} 统一映射为 HTTP 状态码并返回
 * {@link com.example.rag.common.exception.ApiErrorResponse}。</p>
 *
 * <p>异常层次结构：</p>
 * <ul>
 *   <li>{@link com.example.rag.common.exception.BusinessValidationException} - 400 业务校验失败</li>
 *   <li>{@link com.example.rag.common.exception.UnauthorizedException} - 401 未认证</li>
 *   <li>{@link com.example.rag.common.exception.ForbiddenException} - 403 越权访问</li>
 *   <li>{@link com.example.rag.common.exception.ResourceNotFoundException} - 404 资源不存在</li>
 * </ul>
 */
package com.example.rag.common.exception;
