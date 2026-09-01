package com.example.rag.common.constant;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 文件扩展名常量单元测试。验证与原 ALLOWED_EXTENSIONS 集合保持一致。
 */
class FileConstantsTest {

    @Test
    void allowedExtensionsContainsCommonDocumentFormats() {
        Set<String> extensions = FileConstants.ALLOWED_EXTENSIONS;
        assertTrue(extensions.contains("pdf"));
        assertTrue(extensions.contains("doc"));
        assertTrue(extensions.contains("docx"));
        assertTrue(extensions.contains("xls"));
        assertTrue(extensions.contains("xlsx"));
        assertTrue(extensions.contains("ppt"));
        assertTrue(extensions.contains("pptx"));
        assertTrue(extensions.contains("md"));
        assertTrue(extensions.contains("txt"));
    }

    @Test
    void allowedExtensionsHasExpectedSize() {
        // 防止新增/删除扩展名时未同步更新 DocParseService / KnowledgeBaseService
        assertEquals(9, FileConstants.ALLOWED_EXTENSIONS.size());
    }

    @Test
    void maxFileBytesMatchesDocumentedLimit() {
        assertEquals(50L * 1024 * 1024, FileConstants.MAX_FILE_BYTES);
    }
}