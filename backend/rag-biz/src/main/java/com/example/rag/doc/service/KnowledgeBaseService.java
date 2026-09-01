package com.example.rag.doc.service;

import com.example.rag.doc.dto.AllowedDepartmentsResponse;
import com.example.rag.doc.dto.CreateKnowledgeBaseRequest;
import com.example.rag.doc.dto.KnowledgeBaseDocumentResponse;
import com.example.rag.doc.dto.KnowledgeBaseResponse;
import com.example.rag.doc.dto.UpdateAllowedDepartmentsRequest;
import com.example.rag.doc.dto.UpdateKnowledgeBaseRequest;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * 知识库业务服务接口。负责权限校验（读/管理/管理员）、DTO 转换、
 * 上传验证（大小、扩展名、内容嗅探）、MinIO 对象键构建、
 * 文档摄入触发和文档生命周期管理。
 *
 * <p>控制器应依赖此接口，不应直接访问持久层或 MinIO 客户端。</p>
 */
public interface KnowledgeBaseService {

    /**
     * 允许上传的文件扩展名列表。必须与 {@code DocParseService.EXPECTED_MEDIA_TYPES} 保持同步。
     */
    java.util.Set<String> ALLOWED_EXTENSIONS =
            java.util.Set.of("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "md", "txt");

    /** 单个文件最大大小：50MB */
    long MAX_FILE_BYTES = 50L * 1024 * 1024;

    // -------- 知识库 CRUD --------

    /**
     * 获取当前用户可访问的知识库列表。
     *
     * @param username 用户名
     * @return 可访问的知识库列表
     */
    List<KnowledgeBaseResponse> listAccessibleBases(String username);

    /**
     * 获取知识库详情。
     *
     * @param username 用户名
     * @param id      知识库 ID
     * @return 知识库信息
     */
    KnowledgeBaseResponse getBase(String username, long id);

    /**
     * 创建知识库。
     *
     * @param username 用户名
     * @param req     创建请求
     * @return 创建的知识库信息
     */
    KnowledgeBaseResponse createBase(String username, CreateKnowledgeBaseRequest req);

    /**
     * 更新知识库信息。
     *
     * @param username 用户名
     * @param id      知识库 ID
     * @param req     更新内容
     * @return 更新后的知识库信息
     */
    KnowledgeBaseResponse updateBase(String username, long id, UpdateKnowledgeBaseRequest req);

    /**
     * 删除知识库。
     *
     * @param username 用户名
     * @param id      知识库 ID
     */
    void deleteBase(String username, long id);

    /**
     * 获取知识库允许访问的部门列表。
     *
     * @param username 用户名
     * @param id      知识库 ID
     * @return 部门 ID 列表
     */
    AllowedDepartmentsResponse listAllowedDepartments(String username, long id);

    /**
     * 更新知识库允许访问的部门列表。
     *
     * @param username 用户名
     * @param id      知识库 ID
     * @param req     新的部门 ID 列表
     * @return 更新后的知识库信息
     */
    KnowledgeBaseResponse updateAllowedDepartments(String username, long id, UpdateAllowedDepartmentsRequest req);

    // -------- 文档管理 --------

    /**
     * 获取知识库下的文档列表。
     *
     * @param username 用户名
     * @param kbId    知识库 ID
     * @return 文档列表
     */
    List<KnowledgeBaseDocumentResponse> listDocuments(String username, long kbId);

    /**
     * 上传文档。上传后自动触发解析、分块和向量化流程。
     *
     * @param username 用户名
     * @param kbId    知识库 ID
     * @param file    上传的文件
     * @return 文档信息
     */
    KnowledgeBaseDocumentResponse uploadDocument(String username, long kbId, MultipartFile file);

    /**
     * 获取文档详情。
     *
     * @param username 用户名
     * @param docId   文档 ID
     * @return 文档信息
     */
    KnowledgeBaseDocumentResponse getDocument(String username, long docId);

    /**
     * 删除文档。
     *
     * @param username 用户名
     * @param docId   文档 ID
     */
    void deleteDocument(String username, long docId);

    /**
     * 重新索引文档。
     *
     * @param username 用户名
     * @param docId   文档 ID
     * @return 更新后的文档信息
     */
    KnowledgeBaseDocumentResponse reindexDocument(String username, long docId);

    /**
     * 订阅文档处理进度事件。
     *
     * @param username 用户名
     * @param docId   文档 ID
     * @return SSE 事件发射器
     */
    SseEmitter subscribeProcessingEvents(String username, long docId);
}
