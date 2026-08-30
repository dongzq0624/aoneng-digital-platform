package com.example.rag.controller;

import com.example.rag.service.*;
import io.minio.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.InputStream;
import java.util.*;

@RestController
@RequestMapping("/api/kb")
public class KnowledgeBaseController {
    private static final Set<String> ALLOWED = Set.of("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "md", "txt");
    private final PlatformRepository repo;
    private final MinioClient minio;
    private final DocParseService parser;
    private final DocumentProcessingService documentProcessor;
    private final String bucket;

    public KnowledgeBaseController(PlatformRepository repo, MinioClient minio, DocParseService parser,
                                   DocumentProcessingService documentProcessor, @Value("${minio.bucket:rag-docs}") String bucket) {
        this.repo = repo;
        this.minio = minio;
        this.parser = parser;
        this.documentProcessor = documentProcessor;
        this.bucket = bucket;
        ensureBucket();
    }

    private void ensureBucket() {
        try {
            if (!minio.bucketExists(BucketExistsArgs.builder().bucket(bucket).build()))
                minio.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
        } catch (Exception ignored) {
        }
    }

    @GetMapping("/bases")
    public List<Map<String, Object>> bases(@AuthenticationPrincipal String username) {
        return repo.accessibleBases(scope(username));
    }

    @PostMapping("/bases")
    public Map<String, Object> create(@AuthenticationPrincipal String username, @RequestBody Map<String, Object> req) {
        PlatformRepository.KbScope scope = scope(username);
        try {
            long id = repo.createBase(req, scope);
            return repo.baseForScope(id, scope);
        } catch (SecurityException e) { throw forbidden(e); }
        catch (IllegalArgumentException e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e); }
    }

    @GetMapping("/bases/{id}")
    public Map<String, Object> detail(@AuthenticationPrincipal String username, @PathVariable long id) {
        return readableBase(id, scope(username));
    }

    @PutMapping("/bases/{id}")
    public Map<String, Object> update(@AuthenticationPrincipal String username, @PathVariable long id, @RequestBody Map<String, Object> req) {
        PlatformRepository.KbScope scope = scope(username);
        requireManage(id, scope);
        try {
            repo.updateBase(id, req, scope);
            return repo.baseForScope(id, scope);
        } catch (SecurityException e) { throw forbidden(e); }
        catch (IllegalArgumentException e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e); }
    }

    @DeleteMapping("/bases/{id}")
    public Map<String, Object> delete(@AuthenticationPrincipal String username, @PathVariable long id) {
        requireManage(id, scope(username));
        repo.deleteBase(id);
        return Map.of("id", id, "deleted", true);
    }

    @GetMapping("/bases/{id}/departments")
    public Map<String, Object> allowedDepartments(@AuthenticationPrincipal String username, @PathVariable long id) {
        PlatformRepository.KbScope scope = scope(username);
        requireAdmin(scope);
        readableBase(id, scope);
        return Map.of("departmentIds", repo.allowedDepartmentIds(id));
    }

    @PutMapping("/bases/{id}/departments")
    public Map<String, Object> updateAllowedDepartments(@AuthenticationPrincipal String username, @PathVariable long id, @RequestBody Map<String, Object> req) {
        PlatformRepository.KbScope scope = scope(username);
        requireAdmin(scope);
        try {
            repo.updateAllowedDepartments(id, numbers(req.get("departmentIds")), scope);
            return repo.baseForScope(id, scope);
        } catch (SecurityException e) { throw forbidden(e); }
        catch (IllegalArgumentException e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e); }
    }

    @GetMapping("/bases/{id}/docs")
    public List<Map<String, Object>> docs(@AuthenticationPrincipal String username, @PathVariable long id) {
        readableBase(id, scope(username));
        return repo.docs(id);
    }

    @PostMapping("/bases/{id}/docs")
    public Map<String, Object> upload(@AuthenticationPrincipal String username, @PathVariable long id, @RequestParam MultipartFile file) {
        PlatformRepository.KbScope scope = scope(username);
        requireManage(id, scope);
        if (file.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "文件不能为空");
        if (file.getSize() > 50L * 1024 * 1024)
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "文件大小不能超过 50MB");
            String name = Optional.ofNullable(file.getOriginalFilename()).orElse("未命名文件");
        String ext = name.contains(".") ? name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT) : "";
        if (!ALLOWED.contains(ext))
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "不支持的文件类型");
        try (InputStream input = file.getInputStream()) {
            parser.validateUpload(input, name, ext);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, e.getMessage(), e);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "无法校验文件完整性", e);
        }
        String key = UUID.randomUUID() + "-" + name;
        try {
            minio.putObject(PutObjectArgs.builder().bucket(bucket).object(key).stream(file.getInputStream(), file.getSize(), -1).contentType(file.getContentType()).build());
            long docId = repo.createDoc(id, name, ext, file.getSize(), key, scope.userId());
            documentProcessor.start(docId, id, key);
            return repo.doc(docId);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "文件存储失败", e);
        }
    }

    @GetMapping("/docs/{id}")
    public Map<String, Object> doc(@AuthenticationPrincipal String username, @PathVariable long id) {
        Map<String, Object> document = repo.doc(id);
        readableBase(((Number) document.get("kbId")).longValue(), scope(username));
        return document;
    }

    @DeleteMapping("/docs/{id}")
    public Map<String, Object> deleteDoc(@AuthenticationPrincipal String username, @PathVariable long id) {
        Map<String, Object> document = repo.doc(id);
        requireManage(((Number) document.get("kbId")).longValue(), scope(username));
        try {
            minio.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(String.valueOf(document.get("objectKey"))).build());
        } catch (Exception ignored) {
        }
        repo.deleteDoc(id);
        return Map.of("docId", id, "deleted", true);
    }

    @PostMapping("/docs/{id}/reindex")
    public Map<String, Object> reindex(@AuthenticationPrincipal String username, @PathVariable long id) {
        Map<String, Object> d = repo.doc(id);
        requireManage(((Number) d.get("kbId")).longValue(), scope(username));
        if (!documentProcessor.start(id, ((Number) d.get("kbId")).longValue(), String.valueOf(d.get("objectKey")))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "该文档正在处理中，请勿重复提交");
        }
        return repo.doc(id);
    }

    @GetMapping(value = "/docs/{id}/processing-events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter processingEvents(@AuthenticationPrincipal String username, @PathVariable long id) {
        Map<String, Object> document = repo.doc(id);
        requireManage(((Number) document.get("kbId")).longValue(), scope(username));
        return documentProcessor.subscribe(id);
    }

    private PlatformRepository.KbScope scope(String username) {
        try { return repo.kbScope(username); }
        catch (IllegalArgumentException e) { throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, e.getMessage(), e); }
    }

    private Map<String, Object> readableBase(long id, PlatformRepository.KbScope scope) {
        try { return repo.baseForScope(id, scope); }
        catch (SecurityException e) { throw forbidden(e); }
        catch (Exception e) { throw new ResponseStatusException(HttpStatus.NOT_FOUND, "知识库不存在", e); }
    }

    private void requireManage(long id, PlatformRepository.KbScope scope) {
        if (!repo.canManageBase(id, scope)) throw forbidden(new SecurityException("无权操作该知识库"));
    }

    private void requireAdmin(PlatformRepository.KbScope scope) {
        if (!scope.admin()) throw forbidden(new SecurityException("仅系统管理员可配置知识库部门权限"));
    }

    private List<Long> numbers(Object raw) {
        if (!(raw instanceof List<?> values)) return List.of();
        return values.stream().filter(Number.class::isInstance).map(Number.class::cast).map(Number::longValue).distinct().toList();
    }

    private ResponseStatusException forbidden(SecurityException e) { return new ResponseStatusException(HttpStatus.FORBIDDEN, e.getMessage(), e); }
}
