package com.aoneng.rag.infra.handler;

import com.aoneng.rag.common.exception.BusinessValidationException;
import com.aoneng.rag.common.exception.ForbiddenException;
import com.aoneng.rag.common.exception.ResourceNotFoundException;
import com.aoneng.rag.common.exception.UnauthorizedException;
import com.aoneng.rag.common.result.Result;
import com.aoneng.rag.common.result.ResultCode;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final HttpServletRequest request = mock(HttpServletRequest.class);

    GlobalExceptionHandlerTest() {
        when(request.getRequestURI()).thenReturn("/api/test");
    }

    @Test
    void unauthorizedMapsTo401() {
        ResponseEntity<Result<Void>> response = handler.unauthorized(
                new UnauthorizedException("token invalid"), request);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals(ResultCode.UNAUTHORIZED, response.getBody().code());
    }

    @Test
    void forbiddenMapsTo403() {
        ResponseEntity<Result<Void>> response = handler.forbidden(
                new ForbiddenException("无权访问"), request);
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals(ResultCode.FORBIDDEN, response.getBody().code());
    }

    @Test
    void notFoundMapsTo404() {
        ResponseEntity<Result<Void>> response = handler.notFound(
                new ResourceNotFoundException("资源不存在"), request);
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals(ResultCode.NOT_FOUND, response.getBody().code());
    }

    @Test
    void businessValidationMapsTo400() {
        ResponseEntity<Result<Void>> response = handler.badRequest(
                new BusinessValidationException("参数错误"), request);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(ResultCode.BAD_REQUEST, response.getBody().code());
    }

    @Test
    void illegalArgumentMapsTo400() {
        ResponseEntity<Result<Void>> response = handler.badRequest(
                new IllegalArgumentException("参数无效"), request);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(ResultCode.BAD_REQUEST, response.getBody().code());
    }

    @Test
    void noSuchElementMapsTo400() {
        ResponseEntity<Result<Void>> response = handler.badRequest(
                new NoSuchElementException("找不到元素"), request);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(ResultCode.BAD_REQUEST, response.getBody().code());
    }

    @Test
    void unexpectedMapsTo500() {
        ResponseEntity<Result<Void>> response = handler.unexpected(
                new RuntimeException("unexpected"), request);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals(ResultCode.INTERNAL_ERROR, response.getBody().code());
        assertNotNull(response.getBody());
    }
}
