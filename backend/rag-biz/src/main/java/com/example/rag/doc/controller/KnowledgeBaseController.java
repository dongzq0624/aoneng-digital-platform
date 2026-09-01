package com.example.rag.doc.controller;

import com.example.rag.common.result.Result;
import com.example.rag.doc.dto.AllowedDepartmentsResponse;
import com.example.rag.doc.dto.CreateKnowledgeBaseRequest;
import com.example.rag.doc.dto.DeleteResponse;
import com.example.rag.doc.dto.KnowledgeBaseDocumentResponse;
import com.example.rag.doc.dto.KnowledgeBaseResponse;
import com.example.rag.doc.dto.UpdateAllowedDepartmentsRequest;
import com.example.rag.doc.dto.UpdateKnowledgeBaseRequest;
import com.example.rag.doc.service.KnowledgeBaseService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * 知识库和文档管理入口。负责 HTTP 协议层面的请求校验和响应封装，
 * 所有知识库创建、文档上传、权限管理等业务逻辑委托给 {@link KnowledgeBaseService}。
 */
@RestController
@RequestMapping("/api/kb")
public class KnowledgeBaseController {

    private final KnowledgeBaseService service;

    public KnowledgeBaseController(KnowledgeBaseService service) {
        this.service = service;
    }

    // -------- 知识库管理 --------

    /**
     * 获取当前用户可访问的知识库列表。
     *
     * @param username 当前登录用户名
     * @return 可访问的知识库列表
     */
    @GetMapping("/bases")
    public Result<List<KnowledgeBaseResponse>> bases(@AuthenticationPrincipal String username) {
        return Result.ok(service.listAccessibleBases(username));
    }

    /**
     * 创建新的知识库。
     *
     * @param username 当前登录用户名
     * @param req      知识库创建请求
     * @return 创建的知识库信息
     */
    @PostMapping("/bases")
    public Result<KnowledgeBaseResponse> create(@AuthenticationPrincipal String username,
                                               @Valid @RequestBody CreateKnowledgeBaseRequest req) {
        return Result.ok(service.createBase(username, req));
    }

    /**
     * 获取知识库详情。
     *
     * @param username 当前登录用户名
     * @param id       知识库 ID
     * @return 知识库详细信息
     */
    @GetMapping("/bases/{id}")
    public Result<KnowledgeBaseResponse> detail(@AuthenticationPrincipal String username,
                                                 @PathVariable long id) {
        return Result.ok(service.getBase(username, id));
    }

    /**
     * 更新知识库信息。
     *
     * @param username 当前登录用户名
     * @param id       知识库 ID
     * @param req      更新内容
     * @return 更新后的知识库信息
     */
    @PutMapping("/bases/{id}")
    public Result<KnowledgeBaseResponse> update(@AuthenticationPrincipal String username,
                                               @PathVariable long id,
                                               @Valid @RequestBody UpdateKnowledgeBaseRequest req) {
        return Result.ok(service.updateBase(username, id, req));
    }

    /**
     * 删除知识库（同时删除关联文档和向量）。
     *
     * @param username 当前登录用户名
     * @param id       知识库 ID
     * @return 删除结果
     */
    @DeleteMapping("/bases/{id}")
    public Result<DeleteResponse> delete(@AuthenticationPrincipal String username,
                                        @PathVariable long id) {
        service.deleteBase(username, id);
        return Result.ok(new DeleteResponse(id, true));
    }

    /**
     * 获取知识库允许访问的部门列表。
     *
     * @param username 当前登录用户名
     * @param id       知识库 ID
     * @return 允许访问的部门 ID 列表
     */
    @GetMapping("/bases/{id}/departments")
    public Result<AllowedDepartmentsResponse> allowedDepartments(
            @AuthenticationPrincipal String username,
            @PathVariable long id) {
        return Result.ok(service.listAllowedDepartments(username, id));
    }

    /**
     * 更新知识库允许访问的部门列表。
     *
     * @param username 当前登录用户名
     * @param id       知识库 ID
     * @param req      新的部门 ID 列表
     * @return 更新后的知识库信息
     */
    @PutMapping("/bases/{id}/departments")
    public Result<KnowledgeBaseResponse> updateAllowedDepartments(
            @AuthenticationPrincipal String username,
            @PathVariable long id,
            @Valid @RequestBody UpdateAllowedDepartmentsRequest req) {
        return Result.ok(service.updateAllowedDepartments(username, id, req));
    }

    // -------- 文档管理 --------

    /**
     * 获取知识库下的文档列表。
     *
     * @param username 当前登录用户名
     * @param id       知识库 ID
     * @return 文档列表
     */
    @GetMapping("/bases/{id}/docs")
    public Result<List<KnowledgeBaseDocumentResponse>> docs(
            @AuthenticationPrincipal String username,
            @PathVariable long id) {
        return Result.ok(service.listDocuments(username, id));
    }

    /**
     * 上传文档到知识库。上传后自动触发解析、分块和向量化流程。
     *
     * @param username 当前登录用户名
     * @param id       知识库 ID
     * @param file     待上传的文件
     * @return 上传结果（包含文档 ID）
     */
    @PostMapping(value = "/bases/{id}/docs", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<KnowledgeBaseDocumentResponse> upload(
            @AuthenticationPrincipal String username,
            @PathVariable long id,
            @RequestPart MultipartFile file) {
        return Result.ok(service.uploadDocument(username, id, file));
    }

    /**
     * 获取文档详情。
     *
     * @param username 当前登录用户名
     * @param id       文档 ID
     * @return 文档详细信息
     */
    @GetMapping("/docs/{id}")
    public Result<KnowledgeBaseDocumentResponse> doc(
            @AuthenticationPrincipal String username,
            @PathVariable long id) {
        return Result.ok(service.getDocument(username, id));
    }

    /**
     * 删除文档（同时删除关联向量）。
     *
     * @param username 当前登录用户名
     * @param id       文档 ID
     * @return 删除结果
     */
    @DeleteMapping("/docs/{id}")
    public Result<DeleteResponse> deleteDoc(@AuthenticationPrincipal String username,
                                            @PathVariable long id) {
        service.deleteDocument(username, id);
        return Result.ok(new DeleteResponse(id, true));
    }

    /**
     * 重新索引文档。用于文档解析失败或需要重新生成向量时。
     *
     * @param username 当前登录用户名
     * @param id       文档 ID
     * @return 更新后的文档信息
     */
    @PostMapping("/docs/{id}/reindex")
    public Result<KnowledgeBaseDocumentResponse> reindex(
            @AuthenticationPrincipal String username,
            @PathVariable long id) {
        return Result.ok(service.reindexDocument(username, id));
    }

    /**
     * 订阅文档处理进度事件（SSE）。
     *
     * @param username 当前登录用户名
     * @param id       文档 ID
     * @return SSE 事件发射器
     */
    @GetMapping(value = "/docs/{id}/processing-events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter processingEvents(@AuthenticationPrincipal String username,
                                       @PathVariable long id) {
        return service.subscribeProcessingEvents(username, id);
    }
}
