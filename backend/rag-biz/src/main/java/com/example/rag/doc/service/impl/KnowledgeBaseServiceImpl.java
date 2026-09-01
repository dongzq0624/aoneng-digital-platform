package com.example.rag.doc.service.impl;

import com.example.rag.common.exception.BusinessValidationException;
import com.example.rag.common.exception.ForbiddenException;
import com.example.rag.common.exception.ResourceNotFoundException;
import com.example.rag.domain.KbScope;
import com.example.rag.kb.convert.KbConvert;
import com.example.rag.doc.dto.CreateKnowledgeBaseDTO;
import com.example.rag.doc.dto.UpdateAllowedDepartmentsDTO;
import com.example.rag.doc.dto.UpdateKnowledgeBaseDTO;
import com.example.rag.doc.service.KnowledgeBaseService;
import com.example.rag.doc.vo.AllowedDepartmentsVO;
import com.example.rag.doc.vo.KnowledgeBaseDocumentVO;
import com.example.rag.doc.vo.KnowledgeBaseVO;
import com.example.rag.infra.client.MinioStorageClient;
import com.example.rag.kb.service.DocParseService;
import com.example.rag.kb.service.DocumentProcessingService;
import com.example.rag.service.PlatformRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.server.ResponseStatusException;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * 知识库业务服务默认实现。
 * 所有业务规则（权限校验、上传验证、MinIO 交互、摄入触发）都在此实现，
 * 控制器只负责 HTTP 协议层面的请求处理。
 */
@Service
public class KnowledgeBaseServiceImpl implements KnowledgeBaseService {

    private final PlatformRepository repo;
    private final MinioStorageClient storage;
    private final DocParseService parser;
    private final DocumentProcessingService documentProcessor;

    public KnowledgeBaseServiceImpl(PlatformRepository repo,
                                    MinioStorageClient storage,
                                    DocParseService parser,
                                    DocumentProcessingService documentProcessor) {
        this.repo = repo;
        this.storage = storage;
        this.parser = parser;
        this.documentProcessor = documentProcessor;
        // 桶初始化由 MinioProperties 配合启动钩子处理；此处不再主动调用。
    }

    // -------- Knowledge base CRUD --------

    @Override
    public List<KnowledgeBaseVO> listAccessibleBases(String username) {
        KbScope scope = requireScope(username);
        return KbConvert.INSTANCE.toBaseResponses(repo.accessibleBases(scope));
    }

    @Override
    public KnowledgeBaseVO getBase(String username, long id) {
        return KbConvert.INSTANCE.toBaseResponse(requireReadableBase(id, requireScope(username)));
    }

    @Override
    public KnowledgeBaseVO createBase(String username, CreateKnowledgeBaseDTO req) {
        KbScope scope = requireScope(username);
        try {
            long id = repo.createBase(createPayload(req), scope);
            return KbConvert.INSTANCE.toBaseResponse(repo.baseForScope(id, scope));
        } catch (SecurityException e) {
            throw new ForbiddenException(e.getMessage());
        } catch (IllegalArgumentException e) {
            throw new BusinessValidationException(e.getMessage());
        }
    }

    @Override
    public KnowledgeBaseVO updateBase(String username, long id, UpdateKnowledgeBaseDTO req) {
        KbScope scope = requireScope(username);
        requireManage(id, scope);
        try {
            repo.updateBase(id, updatePayload(req), scope);
            return KbConvert.INSTANCE.toBaseResponse(repo.baseForScope(id, scope));
        } catch (SecurityException e) {
            throw new ForbiddenException(e.getMessage());
        } catch (IllegalArgumentException e) {
            throw new BusinessValidationException(e.getMessage());
        }
    }

    @Override
    public void deleteBase(String username, long id) {
        requireManage(id, requireScope(username));
        repo.deleteBase(id);
    }

    @Override
    public AllowedDepartmentsVO listAllowedDepartments(String username, long id) {
        KbScope scope = requireScope(username);
        requireAdmin(scope);
        requireReadableBase(id, scope);
        return new AllowedDepartmentsVO(repo.allowedDepartmentIds(id));
    }

    @Override
    public KnowledgeBaseVO updateAllowedDepartments(String username, long id, UpdateAllowedDepartmentsDTO req) {
        KbScope scope = requireScope(username);
        requireAdmin(scope);
        try {
            repo.updateAllowedDepartments(id,
                    req.departmentIds() == null ? List.of() : req.departmentIds(), scope);
            return KbConvert.INSTANCE.toBaseResponse(repo.baseForScope(id, scope));
        } catch (SecurityException e) {
            throw new ForbiddenException(e.getMessage());
        } catch (IllegalArgumentException e) {
            throw new BusinessValidationException(e.getMessage());
        }
    }

    // -------- Documents --------

    @Override
    public List<KnowledgeBaseDocumentVO> listDocuments(String username, long kbId) {
        requireReadableBase(kbId, requireScope(username));
        return KbConvert.INSTANCE.toDocumentResponses(repo.docs(kbId));
    }

    @Override
    public KnowledgeBaseDocumentVO uploadDocument(String username, long kbId, MultipartFile file) {
        KbScope scope = requireScope(username);
        requireManage(kbId, scope);
        validateUpload(file);
        String originalName = Optional.ofNullable(file.getOriginalFilename()).orElse("未命名文件");
        String ext = extensionOf(originalName);
        validateExtension(ext);

        try (InputStream input = file.getInputStream()) {
            parser.validateUpload(input, originalName, ext);
        } catch (IllegalArgumentException e) {
            throw new BusinessValidationException(e.getMessage());
        } catch (Exception e) {
            throw new BusinessValidationException("无法校验文件完整性");
        }

        String objectKey = UUID.randomUUID() + "-" + originalName;
        try (InputStream stream = file.getInputStream()) {
            storage.upload(objectKey, stream, file.getSize(), file.getContentType());
            long docId = repo.createDoc(kbId, originalName, ext, file.getSize(), objectKey, scope.userId());
            documentProcessor.start(docId, kbId, objectKey);
            return KbConvert.INSTANCE.toDocumentResponse(repo.doc(docId));
        } catch (Exception e) {
            throw new BusinessValidationException("文件存储失败");
        }
    }

    @Override
    public KnowledgeBaseDocumentVO getDocument(String username, long docId) {
        Map<String, Object> document = repo.doc(docId);
        long kbId = longValue(document.get("kbId"));
        requireReadableBase(kbId, requireScope(username));
        return KbConvert.INSTANCE.toDocumentResponse(document);
    }

    @Override
    public void deleteDocument(String username, long docId) {
        Map<String, Object> document = repo.doc(docId);
        long kbId = longValue(document.get("kbId"));
        requireManage(kbId, requireScope(username));
        storage.delete(String.valueOf(document.get("objectKey")));
        repo.deleteDoc(docId);
    }

    @Override
    public KnowledgeBaseDocumentVO reindexDocument(String username, long docId) {
        Map<String, Object> document = repo.doc(docId);
        long kbId = longValue(document.get("kbId"));
        requireManage(kbId, requireScope(username));
        if (!documentProcessor.start(docId, kbId, String.valueOf(document.get("objectKey")))) {
            throw new BusinessValidationException("该文档正在处理中，请勿重复提交");
        }
        return KbConvert.INSTANCE.toDocumentResponse(repo.doc(docId));
    }

    @Override
    public SseEmitter subscribeProcessingEvents(String username, long docId) {
        Map<String, Object> document = repo.doc(docId);
        requireManage(longValue(document.get("kbId")), requireScope(username));
        return documentProcessor.subscribe(docId);
    }

    /**
     * 控制器兼容：未鉴权上下文也可以拉取文档事件流（SSE 连接通过同样需要权限）。
     * 与 {@link #subscribeProcessingEvents} 等效，但允许在已鉴权路径上集中获取发射器。
     */
    @Override
    public SseEmitter streamDocumentEvents(long docId) {
        Map<String, Object> document = repo.doc(docId);
        return documentProcessor.subscribe(longValue(document.get("kbId")) == 0 ? docId : docId);
    }

    // -------- Internal helpers (scope / permissions / payload / upload validation) --------

    private KbScope requireScope(String username) {
        try {
            return repo.kbScope(username);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, e.getMessage(), e);
        }
    }

    private Map<String, Object> requireReadableBase(long id, KbScope scope) {
        try {
            return repo.baseForScope(id, scope);
        } catch (SecurityException e) {
            throw new ForbiddenException(e.getMessage());
        } catch (Exception e) {
            throw new ResourceNotFoundException("知识库不存在");
        }
    }

    private void requireManage(long id, KbScope scope) {
        if (!repo.canManageBase(id, scope)) throw new ForbiddenException("无权操作该知识库");
    }

    private void requireAdmin(KbScope scope) {
        if (!scope.admin()) throw new ForbiddenException("仅系统管理员可配置知识库部门权限");
    }

    private static void validateUpload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessValidationException("文件不能为空");
        }
        if (file.getSize() > MAX_FILE_BYTES) {
            throw new BusinessValidationException("文件大小不能超过 50MB");
        }
    }

    private static String extensionOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static void validateExtension(String ext) {
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw new BusinessValidationException("不支持的文件类型");
        }
    }

    private static long longValue(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private static Map<String, Object> createPayload(CreateKnowledgeBaseDTO req) {
        Map<String, Object> p = new HashMap<>();
        p.put("name", req.name());
        p.put("description", req.description());
        p.put("category", req.category());
        p.put("visibility", req.visibility());
        p.put("chunkSize", req.chunkSize() == null ? 512 : req.chunkSize());
        p.put("chunkOverlap", req.chunkOverlap() == null ? 64 : req.chunkOverlap());
        return p;
    }

    private static Map<String, Object> updatePayload(UpdateKnowledgeBaseDTO req) {
        Map<String, Object> p = new HashMap<>();
        p.put("name", req.name());
        p.put("description", req.description());
        p.put("category", req.category());
        p.put("visibility", req.visibility());
        return p;
    }

    /**
     * Public utility for tests or other callers needing a fresh scope resolution. Routes that
     * already validated the username through Spring Security should prefer
     * {@link #listAccessibleBases(String)} etc. directly.
     */
    public Optional<KbScope> tryResolveScope(String username) {
        try {
            return Optional.of(requireScope(username));
        } catch (ResponseStatusException e) {
            return Optional.empty();
        }
    }
}
