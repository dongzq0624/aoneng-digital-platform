package com.aoneng.rag.doc.controller;

import com.aoneng.rag.common.result.Result;
import com.aoneng.rag.doc.dto.CreateKnowledgeBaseDTO;
import com.aoneng.rag.doc.dto.UpdateAllowedDepartmentsDTO;
import com.aoneng.rag.doc.dto.UpdateKnowledgeBaseDTO;
import com.aoneng.rag.doc.service.KnowledgeBaseService;
import com.aoneng.rag.doc.vo.AllowedDepartmentsVO;
import com.aoneng.rag.doc.vo.DeleteVO;
import com.aoneng.rag.doc.vo.KnowledgeBaseDocumentVO;
import com.aoneng.rag.doc.vo.KnowledgeBaseVO;
import com.aoneng.rag.doc.vo.DocumentParentChunksVO;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.core.io.InputStreamResource;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 知识库和文档管理入口。负责 HTTP 协议层面的请求校验和响应封装，
 * 所有知识库创建、文档上传、权限管理等业务逻辑委托给 {@link KnowledgeBaseService}。
 */
@RestController
@RequestMapping("/api/v1/kb")
public class KnowledgeBaseController {

    private final KnowledgeBaseService service;

    public KnowledgeBaseController(KnowledgeBaseService service) {
        this.service = service;
    }

    // -------- 知识库管理 --------

    @GetMapping("/bases")
    public Result<List<KnowledgeBaseVO>> bases(@AuthenticationPrincipal String username) {
        return Result.ok(service.listAccessibleBases(username));
    }

    @PostMapping("/bases")
    public Result<KnowledgeBaseVO> create(@AuthenticationPrincipal String username,
                                          @Valid @RequestBody CreateKnowledgeBaseDTO req) {
        return Result.ok(service.createBase(username, req));
    }

    @GetMapping("/bases/{id}")
    public Result<KnowledgeBaseVO> detail(@AuthenticationPrincipal String username,
                                          @PathVariable long id) {
        return Result.ok(service.getBase(username, id));
    }

    @PutMapping("/bases/{id}")
    public Result<KnowledgeBaseVO> update(@AuthenticationPrincipal String username,
                                          @PathVariable long id,
                                          @Valid @RequestBody UpdateKnowledgeBaseDTO req) {
        return Result.ok(service.updateBase(username, id, req));
    }

    @DeleteMapping("/bases/{id}")
    public Result<DeleteVO> delete(@AuthenticationPrincipal String username,
                                   @PathVariable long id) {
        service.deleteBase(username, id);
        return Result.ok(new DeleteVO(id, true));
    }

    @GetMapping("/bases/{id}/departments")
    public Result<AllowedDepartmentsVO> allowedDepartments(
            @AuthenticationPrincipal String username,
            @PathVariable long id) {
        return Result.ok(service.listAllowedDepartments(username, id));
    }

    @PutMapping("/bases/{id}/departments")
    public Result<KnowledgeBaseVO> updateAllowedDepartments(
            @AuthenticationPrincipal String username,
            @PathVariable long id,
            @Valid @RequestBody UpdateAllowedDepartmentsDTO req) {
        return Result.ok(service.updateAllowedDepartments(username, id, req));
    }

    // -------- 文档管理 --------

    @GetMapping("/bases/{id}/docs")
    public Result<?> docs(
            @AuthenticationPrincipal String username,
            @PathVariable long id,
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "pageSize", defaultValue = "20") int pageSize) {
        return Result.ok(service.listDocuments(username, id, keyword, page, pageSize));
    }

    @PostMapping(value = "/bases/{id}/docs", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<KnowledgeBaseDocumentVO> upload(
            @AuthenticationPrincipal String username,
            @PathVariable long id,
            @RequestPart MultipartFile file) {
        return Result.ok(service.uploadDocument(username, id, file));
    }

    @GetMapping("/docs/{id}")
    public Result<KnowledgeBaseDocumentVO> doc(
            @AuthenticationPrincipal String username,
            @PathVariable long id) {
        return Result.ok(service.getDocument(username, id));
    }

    @GetMapping("/docs/{id}/parent-chunks")
    public Result<DocumentParentChunksVO> parentChunks(
            @AuthenticationPrincipal String username,
            @PathVariable long id) {
        return Result.ok(service.listParentChunks(username, id));
    }

    @GetMapping("/docs/{id}/preview")
    public ResponseEntity<InputStreamResource> preview(
            @AuthenticationPrincipal String username,
            @PathVariable long id) {
        KnowledgeBaseService.DocumentPreview preview = service.previewDocument(username, id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(preview.contentType()))
                .contentLength(preview.size())
                .header("Content-Disposition", contentDisposition(preview.fileName()))
                .body(new InputStreamResource(preview.stream()));
    }

    private String contentDisposition(String fileName) {
        String safe = fileName == null || fileName.isBlank() ? "document" : fileName.replaceAll("[\\r\\n\\\"]", "_");
        String encoded = URLEncoder.encode(safe, StandardCharsets.UTF_8).replace("+", "%20");
        return "inline; filename=\"document\"; filename*=UTF-8''" + encoded;
    }

    @DeleteMapping("/docs/{id}")
    public Result<DeleteVO> deleteDoc(@AuthenticationPrincipal String username,
                                       @PathVariable long id) {
        service.deleteDocument(username, id);
        return Result.ok(new DeleteVO(id, true));
    }

    @PostMapping("/docs/{id}/reindex")
    public Result<KnowledgeBaseDocumentVO> reindex(@AuthenticationPrincipal String username,
                                                    @PathVariable long id) {
        return Result.ok(service.reindexDocument(username, id));
    }

    @PostMapping("/docs/reindex-all")
    public Result<Map<String, Integer>> reindexAll(@AuthenticationPrincipal String username) {
        return Result.ok(Map.of("started", service.reindexAll(username)));
    }

    /**
     * 文档解析/索引过程的事件流。该实现重新发出 {@link SseEmitter}，
     * 业务侧的事件载荷与 SSE 字段定义保持向后兼容。
     *
     * @param id 文档 ID
     * @return SSE 发射器
     */
    @GetMapping(value = "/docs/{id}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter documentEvents(@AuthenticationPrincipal String username,
                                     @PathVariable long id) {
        return service.subscribeProcessingEvents(username, id);
    }
}
