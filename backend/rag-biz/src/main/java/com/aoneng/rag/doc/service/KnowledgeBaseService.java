package com.aoneng.rag.doc.service;

import com.aoneng.rag.doc.dto.CreateKnowledgeBaseDTO;
import com.aoneng.rag.doc.dto.UpdateAllowedDepartmentsDTO;
import com.aoneng.rag.doc.dto.UpdateKnowledgeBaseDTO;
import com.aoneng.rag.doc.vo.AllowedDepartmentsVO;
import com.aoneng.rag.doc.vo.KnowledgeBaseDocumentVO;
import com.aoneng.rag.doc.vo.KnowledgeBaseVO;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * 知识库业务服务接口。负责权限校验（读/管理/管理员）、VO 转换、
 * 上传验证（大小、扩展名、内容嗅探）、MinIO 对象键构建、
 * 文档摄入触发和文档生命周期管理。
 *
 * <p>控制器应依赖此接口，不应直接访问持久层或 MinIO 客户端。</p>
 */
public interface KnowledgeBaseService {

    /**
     * 允许上传的文件扩展名列表。与 {@link com.aoneng.rag.common.constant.FileConstants#ALLOWED_EXTENSIONS} 保持同步。
     */
    java.util.Set<String> ALLOWED_EXTENSIONS = com.aoneng.rag.common.constant.FileConstants.ALLOWED_EXTENSIONS;

    /** 单个文件最大大小：50MB */
    long MAX_FILE_BYTES = com.aoneng.rag.common.constant.FileConstants.MAX_FILE_BYTES;

    List<KnowledgeBaseVO> listAccessibleBases(String username);

    KnowledgeBaseVO getBase(String username, long id);

    KnowledgeBaseVO createBase(String username, CreateKnowledgeBaseDTO req);

    KnowledgeBaseVO updateBase(String username, long id, UpdateKnowledgeBaseDTO req);

    void deleteBase(String username, long id);

    AllowedDepartmentsVO listAllowedDepartments(String username, long id);

    KnowledgeBaseVO updateAllowedDepartments(String username, long id, UpdateAllowedDepartmentsDTO req);

    List<KnowledgeBaseDocumentVO> listDocuments(String username, long kbId);

    KnowledgeBaseDocumentVO uploadDocument(String username, long kbId, MultipartFile file);

    KnowledgeBaseDocumentVO getDocument(String username, long docId);

    void deleteDocument(String username, long docId);

    KnowledgeBaseDocumentVO reindexDocument(String username, long docId);

    /** Start a controlled reindex for all documents visible to an administrator. */
    int reindexAll(String username);

    /**
     * 订阅文档处理进度事件。
     *
     * @param username 用户名
     * @param docId   文档 ID
     * @return SSE 事件发射器
     */
    SseEmitter subscribeProcessingEvents(String username, long docId);

    /**
     * SSE 入口：返回未鉴权的事件流（兼容历史用法）。
     *
     * @param docId 文档 ID
     * @return SSE 事件发射器
     */
    SseEmitter streamDocumentEvents(long docId);
}
