package com.example.rag.service;

import com.example.rag.common.ScopeValidator;
import com.example.rag.common.exception.ForbiddenException;
import com.example.rag.domain.KbScope;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * ScopeValidator 单元测试。
 */
class ScopeValidatorTest {

    private final ScopeValidator validator = new ScopeValidator();

    @Test
    void adminScopeCanPassRequireAdmin() {
        KbScope adminScope = new KbScope(1L, 1L, true, true, true);
        assertDoesNotThrow(() -> validator.requireAdmin(adminScope));
    }

    @Test
    void nonAdminScopeFailsRequireAdmin() {
        KbScope userScope = new KbScope(1L, 1L, false, true, false);
        ForbiddenException ex = assertThrows(ForbiddenException.class,
                () -> validator.requireAdmin(userScope));
        assertEquals("仅系统管理员可以执行该操作", ex.getMessage());
    }

    @Test
    void scopeWithKbAccessPassesRequireKbAccess() {
        KbScope userScope = new KbScope(1L, 1L, false, true, false);
        assertDoesNotThrow(() -> validator.requireKbAccess(userScope));
    }

    @Test
    void scopeWithoutKbAccessFailsRequireKbAccess() {
        KbScope restricted = new KbScope(1L, 1L, false, false, false);
        assertThrows(ForbiddenException.class, () -> validator.requireKbAccess(restricted));
    }

    @Test
    void nullScopeFailsRequireKbAccess() {
        assertThrows(ForbiddenException.class, () -> validator.requireKbAccess(null));
    }

    @Test
    void requireManageBasePassesWhenCanManage() {
        KbScope scope = new KbScope(1L, 1L, true, true, true);
        assertDoesNotThrow(() -> validator.requireManageBase(1L, scope, true));
    }

    @Test
    void requireManageBaseFailsWhenCannotManage() {
        KbScope scope = new KbScope(1L, 1L, false, true, false);
        assertThrows(ForbiddenException.class, () -> validator.requireManageBase(1L, scope, false));
    }
}
