package com.aoneng.rag.doc.service.impl;

import com.aoneng.rag.application.repository.KbScope;
import com.aoneng.rag.application.repository.PlatformRepository;
import com.aoneng.rag.observability.RagObservability;
import com.aoneng.rag.application.processing.DocumentProcessor;
import com.aoneng.rag.common.exception.BusinessValidationException;
import com.aoneng.rag.common.exception.ForbiddenException;
import com.aoneng.rag.common.exception.ResourceNotFoundException;
import com.aoneng.rag.doc.dto.CreateKnowledgeBaseDTO;
import com.aoneng.rag.doc.dto.UpdateAllowedDepartmentsDTO;
import com.aoneng.rag.doc.dto.UpdateKnowledgeBaseDTO;
import com.aoneng.rag.doc.service.KnowledgeBaseService;
import com.aoneng.rag.doc.vo.AllowedDepartmentsVO;
import com.aoneng.rag.doc.vo.KnowledgeBaseDocumentVO;
import com.aoneng.rag.doc.vo.KnowledgeBaseVO;
import com.aoneng.rag.infra.parse.DocParser;
import com.aoneng.rag.infra.storage.ObjectStorage;
import com.aoneng.rag.application.convert.KbConvert;
import com.aoneng.rag.application.repository.PlatformRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(KnowledgeBaseServiceImpl.class);

    private final PlatformRepository repo;
    private final ObjectStorage storage;
    private final DocParser parser;
    private final DocumentProcessor documentProcessor;
    private final RagObservability observability;

    public KnowledgeBaseServiceImpl(PlatformRepository repo,
                                    ObjectStorage storage,
                                    DocParser parser,
                                    DocumentProcessor documentProcessor,
                                    RagObservability observability) {
        this.repo = repo;
        this.storage = storage;
        this.parser = parser;
        this.documentProcessor = documentProcessor;
        this.observability = observability;
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
        long docId = 0L;
        try (InputStream stream = file.getInputStream()) {
            long storageStarted = System.nanoTime();
            try {
                storage.upload(objectKey, stream, file.getSize(), file.getContentType());
                // 文档记录创建后再写入成功事件，确保文件级监控可以关联 doc_id。
                long uploadDuration = System.nanoTime() - storageStarted;
                ObjectStorage.ObjectInfo objectInfo = storage.stat(objectKey);
                String etag = objectInfo == null ? null : objectInfo.etag();
                docId = repo.createDoc(kbId, originalName, ext, file.getSize(), objectKey, etag, scope.userId());
                observability.record("object.upload", uploadDuration, true,
                        Map.of("doc_id", docId, "kb_id", kbId, "file_size", file.getSize(), "file_type", ext));
                if (!documentProcessor.start(docId, kbId, objectKey)) {
                    throw new IllegalStateException("文档处理任务已存在");
                }
                return KbConvert.INSTANCE.toDocumentResponse(repo.doc(docId));
            } catch (Exception storageFailure) {
                observability.record("object.upload", System.nanoTime() - storageStarted, false,
                        Map.of("kb_id", kbId, "file_size", file.getSize(), "file_type", ext,
                                "error_type", storageFailure.getClass().getSimpleName()));
                throw storageFailure;
            }
        } catch (Exception e) {
            if (docId > 0) {
                try {
                    repo.deleteDoc(docId);
                } catch (Exception cleanupFailure) {
                    log.error("Failed to rollback uploaded document: docId={}", docId, cleanupFailure);
                }
            }
            try {
                storage.delete(objectKey);
            } catch (Exception cleanupFailure) {
                log.error("Failed to rollback uploaded object: objectKey={}", objectKey, cleanupFailure);
            }
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
        documentProcessor.deleteIndex(docId);
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
    public int reindexAll(String username) {
        KbScope scope = requireScope(username);
        if (!scope.admin()) throw new SecurityException("仅管理员可以批量重建文档索引");
        int started = 0;
        for (Map<String, Object> base : repo.bases()) {
            long kbId = longValue(base.get("id"));
            for (Map<String, Object> document : repo.docs(kbId)) {
                long docId = longValue(document.get("id"));
                String key = String.valueOf(document.get("objectKey"));
                if (documentProcessor.start(docId, kbId, key)) started++;
            }
        }
        return started;
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
        p.put("chunkSize", req.chunkSize() == null ? 2_000 : req.chunkSize());
        p.put("chunkOverlap", req.chunkOverlap() == null ? 64 : req.chunkOverlap());
        return p;
    }

    private static Map<String, Object> updatePayload(UpdateKnowledgeBaseDTO req) {
        Map<String, Object> p = new HashMap<>();
        p.put("name", req.name());
        p.put("description", req.description());
        p.put("category", req.category());
        p.put("visibility", req.visibility());
        if (req.chunkSize() != null) p.put("chunkSize", req.chunkSize());
        if (req.chunkOverlap() != null) p.put("chunkOverlap", req.chunkOverlap());
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
