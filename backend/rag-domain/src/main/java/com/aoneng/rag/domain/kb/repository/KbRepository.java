package com.aoneng.rag.domain.kb.repository;

import java.util.List;
import java.util.Map;

/**
 * 知识库仓储接口（kb bounded context）。
 * 定义知识库、文档、分块等知识的查询与变更操作。
 */
public interface KbRepository {

    // -------- Knowledge Base --------

    /** 查询所有知识库概览。 */
    List<Map<String, Object>> findAllBases();

    /** 按 ID 查询知识库。 */
    Map<String, Object> findBaseById(long id);

    /** 创建知识库。 */
    /** Create a knowledge base with parent chunk token limit and overlap. */
    long createBase(String name, String description, String category, String visibility,
                    long ownerId, Long deptId, int chunkSize, int chunkOverlap);

    /** 更新知识库。 */
    /** Update metadata and optional parent chunk token settings. */
    void updateBase(long id, String name, String description, String visibility, Long deptId,
                    Integer chunkSize, Integer chunkOverlap);

    /** 软删除知识库。 */
    void softDeleteBase(long id);

    /** 查询允许访问的部门 ID 列表。 */
    List<Long> findAllowedDepartmentIds(long kbId);

    /** 插入允许部门。 */
    void insertAllowedDept(long kbId, long deptId);

    /** 删除允许部门。 */
    void deleteAllowedDepts(long kbId);

    /** 批量删除允许部门。 */
    void deleteAllowedDepts(List<Long> deptIds);

    /** 统计部门是否有效。 */
    int countActiveDepts(List<Long> deptIds);

    // -------- Document --------

    /** 创建文档记录。 */
    long createDocument(long kbId, String name, String type, long size, String key, long uploader);

    /** 查询文档。 */
    Map<String, Object> findDocumentById(long id);

    /** 按知识库查询文档列表。 */
    List<Map<String, Object>> findDocumentsByKb(long kbId);

    /** 更新文档状态。 */
    void updateDocumentStatus(long id, String parse, String chunk, int count, String error);

    /** 软删除文档。 */
    void softDeleteDocument(long id);

    // -------- Chunk --------

    long saveBaseChunk(long docId, long kbId, int seq, String content, Integer pageNo,
                       int tokenCount, String blockType, String metadata);

    void updateParentEmbeddingId(long parentId, String embeddingId);

    /** 保存分块。 */
    long saveParentChunk(long docId, long kbId, int seq, String content, Integer pageNo, int tokenCount);

    long saveChunk(long docId, long kbId, long parentId, int seq, String content, Integer pageNo, int tokenCount);

    /** Persist the external vector-store point identifier after the chunk row has an ID. */
    void updateChunkEmbeddingId(long chunkId, String embeddingId);

    void updateChunkMetadata(long chunkId, String metadata);

    Map<String, Object> findParentChunk(long parentId);

    /** 删除文档的分块。 */
    void deleteChunksByDoc(long docId);

    /** 关键字搜索分块。 */
    List<Map<String, Object>> keywordSearch(List<String> terms, List<Long> allowedKbIds, int limit);
}
