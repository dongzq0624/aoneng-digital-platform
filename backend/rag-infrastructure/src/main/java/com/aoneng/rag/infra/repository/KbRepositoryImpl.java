package com.aoneng.rag.infra.repository;

import com.aoneng.rag.domain.kb.mapper.KbBaseMapper;
import com.aoneng.rag.domain.kb.mapper.KbChunkMapper;
import com.aoneng.rag.domain.kb.mapper.KbDocumentMapper;
import com.aoneng.rag.domain.kb.po.KbBasePO;
import com.aoneng.rag.domain.kb.po.KbChunkPO;
import com.aoneng.rag.domain.kb.po.KbDocumentPO;
import com.aoneng.rag.domain.kb.repository.KbRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
public class KbRepositoryImpl implements KbRepository {
    private final KbBaseMapper baseMapper;
    private final KbDocumentMapper documentMapper;
    private final KbChunkMapper chunkMapper;

    public KbRepositoryImpl(KbBaseMapper baseMapper, KbDocumentMapper documentMapper, KbChunkMapper chunkMapper) {
        this.baseMapper = baseMapper;
        this.documentMapper = documentMapper;
        this.chunkMapper = chunkMapper;
    }

    @Override
    public List<Map<String, Object>> findAllBases() {
        return baseMapper.selectBaseOverviews();
    }

    @Override
    public Map<String, Object> findBaseById(long id) {
        return baseMapper.selectBaseOverview(id);
    }

    @Override
    public long createBase(String name, String description, String category, String visibility, long ownerId, Long deptId, int chunkSize, int chunkOverlap) {
        KbBasePO p = new KbBasePO();
        p.setName(name);
        p.setDescription(description);
        p.setCategory(category);
        p.setVisibility(visibility);
        p.setOwnerId(ownerId);
        p.setDeptId(deptId);
        p.setChunkSize(chunkSize);
        p.setChunkOverlap(chunkOverlap);
        baseMapper.insertReturningId(p);
        return p.getId();
    }

    @Override
    public void updateBase(long id, String name, String description, String visibility, Long deptId,
                           Integer chunkSize, Integer chunkOverlap) {
        baseMapper.updateBase(id, name, description, visibility, deptId, chunkSize, chunkOverlap);
    }

    @Override
    public void softDeleteBase(long id) {
        baseMapper.softDelete(id);
    }

    @Override
    public List<Long> findAllowedDepartmentIds(long kbId) {
        return baseMapper.selectAllowedDeptIds(kbId);
    }

    @Override
    public void insertAllowedDept(long kbId, long deptId) {
        baseMapper.insertAllowedDept(kbId, deptId);
    }

    @Override
    public void deleteAllowedDepts(long kbId) {
        baseMapper.deleteAllowedDepts(kbId);
    }

    @Override
    public void deleteAllowedDepts(List<Long> deptIds) {
        if (deptIds == null || deptIds.isEmpty()) return;
        baseMapper.deleteAllowedDeptsByDeptIds(deptIds);
    }

    @Override
    public int countActiveDepts(List<Long> deptIds) {
        return deptIds == null || deptIds.isEmpty() ? 0 : baseMapper.countActiveDepts(deptIds);
    }

    @Override
    public long createDocument(long kbId, String name, String type, long size, String key, String etag, long uploader) {
        KbDocumentPO p = new KbDocumentPO();
        p.setKbId(kbId);
        p.setFileName(name);
        p.setFileType(type);
        p.setFileSize(size);
        p.setObjectKey(key);
        p.setObjectEtag(etag);
        p.setUploaderId(uploader);
        documentMapper.insertReturningId(p);
        return p.getId();
    }

    @Override
    public Map<String, Object> findDocumentById(long id) {
        return documentMapper.selectDocument(id);
    }

    @Override
    public List<Map<String, Object>> findDocumentsByKb(long kbId) {
        return documentMapper.selectDocumentsByKb(kbId);
    }

    @Override
    public void updateDocumentStatus(long id, String parse, String chunk, int count, String error) {
        documentMapper.updateStatus(id, parse, chunk, count, error);
    }

    @Override
    public boolean initializeDocumentFingerprint(long id, String etag, long size) {
        return documentMapper.initializeFingerprint(id, etag, size) > 0;
    }

    @Override
    public boolean markDocumentChanged(long id, String etag, long size) {
        return documentMapper.markObjectChanged(id, etag, size) > 0;
    }

    @Override
    public void touchDocumentScan(long id) {
        documentMapper.touchScan(id);
    }

    @Override
    public void enqueueIndexTask(long docId, int version, String operation) {
        documentMapper.insertIndexTask(docId, version, operation);
    }

    @Override
    public void updateIndexTask(long docId, int version, String operation, String status, String error) {
        documentMapper.updateIndexTask(docId, version, operation, status, error);
    }

    @Override
    public int resetStaleIndexTasks(int timeoutMinutes) {
        return documentMapper.resetStaleIndexTasks(timeoutMinutes);
    }

    @Override
    public List<Map<String, Object>> dueIndexTasks(int limit) {
        return documentMapper.selectDueIndexTasks(limit);
    }

    @Override
    public void softDeleteDocument(long id) {
        documentMapper.softDelete(id);
    }

    @Override
    public long saveBaseChunk(long docId, long kbId, int seq, String content, Integer pageNo,
                              int tokenCount, String blockType, String metadata, boolean tokenEstimated) {
        Long id = chunkMapper.insertBase(docId, kbId, seq, content, pageNo, tokenCount,
                blockType, metadata == null || metadata.isBlank() ? "{}" : metadata, tokenEstimated);
        if (id == null) throw new IllegalStateException("基础分块保存失败");
        return id;
    }

    @Override
    public void updateParentEmbeddingId(long parentId, String embeddingId) {
        if (parentId <= 0 || embeddingId == null || embeddingId.isBlank()
                || chunkMapper.updateParentEmbeddingId(parentId, embeddingId) != 1) {
            throw new IllegalStateException("父块向量标识写入失败");
        }
    }

    @Override
    public long saveParentChunk(long docId, long kbId, int seq, String content, Integer pageNo, int tokenCount,
                                String metadata, boolean tokenEstimated) {
        Long id = chunkMapper.insertParent(docId, kbId, seq, content, pageNo, tokenCount,
                metadata == null || metadata.isBlank() ? "{}" : metadata, tokenEstimated);
        if (id == null) throw new IllegalStateException("父块保存失败");
        return id;
    }

    @Override
    public long saveChunk(long docId, long kbId, long parentId, int seq, String content, Integer pageNo,
                          int tokenCount, boolean tokenEstimated) {
        KbChunkPO p = new KbChunkPO();
        p.setDocId(docId);
        p.setKbId(kbId);
        p.setParentId(parentId);
        p.setSeq(seq);
        p.setContent(content);
        p.setPageNo(pageNo);
        p.setTokenCount(tokenCount);
        p.setTokenCountEstimated(tokenEstimated);
        chunkMapper.insertReturningId(p);
        return p.getId();
    }

    @Override
    public void updateChunkEmbeddingId(long chunkId, String embeddingId) {
        if (chunkId <= 0 || embeddingId == null || embeddingId.isBlank()) {
            throw new IllegalArgumentException("分块向量标识不能为空");
        }
        int updated = chunkMapper.updateEmbeddingId(chunkId, embeddingId);
        if (updated != 1) {
            throw new IllegalStateException("分块向量标识写入失败");
        }
    }

    @Override
    public void updateChunkMetadata(long chunkId, String metadata) {
        if (chunkId <= 0 || metadata == null || metadata.isBlank()) throw new IllegalArgumentException("分块布局元数据不能为空");
        if (chunkMapper.updateMetadata(chunkId, metadata) != 1) throw new IllegalStateException("分块布局元数据写入失败");
    }

    @Override
    public Map<String, Object> findParentChunk(long parentId) {
        return chunkMapper.selectParent(parentId);
    }

    @Override
    public List<Map<String, Object>> findParentChunksByDoc(long docId) {
        return chunkMapper.selectParentsByDoc(docId);
    }

    @Override
    public void deleteChunksByDoc(long docId) {
        chunkMapper.deleteByDoc(docId);
        chunkMapper.deleteParentsByDoc(docId);
        chunkMapper.deleteBaseByDoc(docId);
    }

    @Override
    public List<Map<String, Object>> keywordSearch(List<String> terms, List<Long> allowedKbIds, int limit) {
        if (terms == null || terms.isEmpty() || allowedKbIds == null || allowedKbIds.isEmpty()) return List.of();
        return chunkMapper.keywordSearch(allowedKbIds, terms, limit);
    }
}
