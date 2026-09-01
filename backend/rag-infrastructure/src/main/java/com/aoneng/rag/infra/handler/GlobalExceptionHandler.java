package com.aoneng.rag.infra.handler;

import com.aoneng.rag.common.exception.ApiErrorResponse;
import com.aoneng.rag.common.exception.BusinessValidationException;
import com.aoneng.rag.common.exception.ForbiddenException;
import com.aoneng.rag.common.exception.ResourceNotFoundException;
import com.aoneng.rag.common.exception.UnauthorizedException;
import com.aoneng.rag.common.result.Result;
import com.aoneng.rag.common.result.ResultCode;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

/**
 * 全局异常处理器。把各类异常统一映射到 {@link Result} 响应体，使前端成功 / 错误响应结构一致。
 * 映射规则：
 * <ul>
 *   <li>{@link MethodArgumentNotValidException} → 400 BAD_REQUEST，附带字段错误</li>
 *   <li>{@link UnauthorizedException} → 401 UNAUTHORIZED</li>
 *   <li>{@link ForbiddenException} → 403 FORBIDDEN</li>
 *   <li>{@link ResourceNotFoundException} → 404 NOT_FOUND</li>
 *   <li>{@link BusinessValidationException} / {@link IllegalArgumentException} → 400 BAD_REQUEST</li>
 *   <li>{@link ResponseStatusException} → 按状态码映射</li>
 *   <li>其余异常 → 500 INTERNAL_ERROR</li>
 * </ul>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Bean validation 失败处理：把字段错误以 "{字段} {提示}" 形式拼接，便于前端展示。
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Void>> validation(MethodArgumentNotValidException exception,
                                                    HttpServletRequest request) {
        StringBuilder details = new StringBuilder();
        for (FieldError error : exception.getBindingResult().getFieldErrors()) {
            if (details.length() > 0) details.append("; ");
            details.append(error.getField()).append(' ').append(error.getDefaultMessage());
        }
        return response(HttpStatus.BAD_REQUEST, ResultCode.BAD_REQUEST, details.toString(), request);
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<Result<Void>> unauthorized(UnauthorizedException exception, HttpServletRequest request) {
        return response(HttpStatus.UNAUTHORIZED, ResultCode.UNAUTHORIZED, exception.getMessage(), request);
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<Result<Void>> forbidden(ForbiddenException exception, HttpServletRequest request) {
        return response(HttpStatus.FORBIDDEN, ResultCode.FORBIDDEN, exception.getMessage(), request);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Result<Void>> notFound(ResourceNotFoundException exception, HttpServletRequest request) {
        return response(HttpStatus.NOT_FOUND, ResultCode.NOT_FOUND, exception.getMessage(), request);
    }

    @ExceptionHandler({BusinessValidationException.class, IllegalArgumentException.class})
    public ResponseEntity<Result<Void>> badRequest(RuntimeException exception, HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, ResultCode.BAD_REQUEST, exception.getMessage(), request);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Result<Void>> status(ResponseStatusException exception, HttpServletRequest request) {
        HttpStatus status = HttpStatus.resolve(exception.getStatusCode().value());
        if (status == null) status = HttpStatus.INTERNAL_SERVER_ERROR;
        String message = exception.getReason() == null ? status.getReasonPhrase() : exception.getReason();
        String code = switch (status) {
            case UNAUTHORIZED -> ResultCode.UNAUTHORIZED;
            case FORBIDDEN -> ResultCode.FORBIDDEN;
            case NOT_FOUND -> ResultCode.NOT_FOUND;
            case CONFLICT -> ResultCode.CONFLICT;
            case BAD_REQUEST -> ResultCode.BAD_REQUEST;
            default -> status.name();
        };
        return response(status, code, message, request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> unexpected(Exception exception, HttpServletRequest request) {
        log.error("Unhandled server exception, path={}", request.getRequestURI(), exception);
        return response(HttpStatus.INTERNAL_SERVER_ERROR, ResultCode.INTERNAL_ERROR, "服务暂时不可用，请稍后重试", request);
    }

    /**
     * 构造统一响应体。null message 会被替换为空串，避免序列化出错。
     */
    private ResponseEntity<Result<Void>> response(HttpStatus status, String code, String message,
                                                   HttpServletRequest request) {
        return ResponseEntity.status(status).body(Result.error(code, message == null ? "" : message));
    }

    /**
     * Legacy {@link ApiErrorResponse} factory kept for compatibility with callers that still
     * depend on the structured error envelope.
     */
    public static ApiErrorResponse legacyError(java.time.Instant now, int status, String code,
                                                String message, String path, java.util.Map<String, String> details) {
        return new ApiErrorResponse(now, status, code, message, path, details);
    }
}
