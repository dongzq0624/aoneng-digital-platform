package com.example.rag.infra.advice;

import com.example.rag.common.result.Result;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * 控制器返回值包装器：把所有非 {@link Result} 的返回值统一包裹在 {@link Result} 信封中。
 * 已经包装过的结果、null（视为空成功）、SSE 流式响应（{@code text/event-stream}）会原样放行。
 */
@RestControllerAdvice(basePackages = "com.example.rag")
public class ResultEnvelopeAdvice implements ResponseBodyAdvice<Object> {

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        // 对所有返回值都生效；具体是否包裹由 beforeBodyWrite 判断。
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  ServerHttpRequest request, ServerHttpResponse response) {
        if (body instanceof Result<?>) return body;
        if (body == null) return Result.ok();
        // Skip SSE / streaming responses so EventSource can parse them.
        String contentType = response.getHeaders().getFirst("Content-Type");
        if (contentType != null && contentType.startsWith(MediaType.TEXT_EVENT_STREAM_VALUE)) {
            return body;
        }
        return Result.ok(body);
    }
}
