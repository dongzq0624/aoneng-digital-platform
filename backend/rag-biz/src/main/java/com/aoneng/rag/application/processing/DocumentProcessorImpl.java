package com.aoneng.rag.application.processing;

import com.aoneng.rag.application.repository.PlatformRepository;
import com.aoneng.rag.infra.chunk.Chunker;
import com.aoneng.rag.infra.chunk.ModelTokenizer;
import com.aoneng.rag.infra.llm.LlmService;
import com.aoneng.rag.infra.parse.DocParser;
import com.aoneng.rag.infra.parse.StructuredDocumentParser;
import com.aoneng.rag.infra.parse.DocumentParseRouter;
import com.aoneng.rag.infra.parse.ScannedPdfParseException;
import com.aoneng.rag.infra.storage.ObjectStorage;
import com.aoneng.rag.observability.RagObservability;
import com.aoneng.rag.infra.vector.VectorStore;
import com.aoneng.rag.infra.vector.Bm25SparseVectorizer;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.SortedMap;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 异步文档处理服务实现。
 * 编排解析 + 分块 + 嵌入 + 向量写入的异步流水线。
 */
@Service
public class DocumentProcessorImpl implements DocumentProcessor {

    private static final Logger log = LoggerFactory.getLogger(DocumentProcessorImpl.class);
    private static final long SSE_TIMEOUT_MS = 0L;
    private static final int PARENT_CHUNK_TOKENS = 1_200;
    private static final int CHILD_CHUNK_TOKENS = 400;
    private static final int CHILD_OVERLAP_TOKENS = 64;

    private final PlatformRepository repo;
    private final ObjectStorage storage;
    private final DocParser parser;
    private final Chunker chunker;
    private final LlmService llm;
    private final VectorStore vectorStore;
    private final Bm25SparseVectorizer sparseVectorizer;
    private final ModelTokenizer modelTokenizer;
    private final TaskExecutor executor;
    private final String bucket;
    private final RagObservability observability;
    private final Map<Long, ProcessingJob> jobs = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public DocumentProcessorImpl(PlatformRepository repo,
                                  ObjectStorage storage,
                                  DocParser parser,
                                  Chunker chunker,
                                  LlmService llm,
                                  VectorStore vectorStore,
                                  Bm25SparseVectorizer sparseVectorizer,
                                  ModelTokenizer modelTokenizer,
                                  @Qualifier("documentProcessingExecutor") TaskExecutor executor,
                                  RagObservability observability,
                                  @Value("${minio.bucket:rag-docs}") String bucket) {
        this.repo = repo;
        this.storage = storage;
        this.parser = parser;
        this.chunker = chunker;
        this.llm = llm;
        this.vectorStore = vectorStore;
        this.sparseVectorizer = sparseVectorizer;
        this.modelTokenizer = modelTokenizer;
        this.executor = executor;
        this.observability = observability;
        this.bucket = bucket;
    }

    @Override
    public boolean start(long docId, long kbId, String objectKey) {
        long started = System.nanoTime();
        ProcessingJob job = jobs.computeIfAbsent(docId, ProcessingJob::new);
        if (!job.running.compareAndSet(false, true)) {
            observability.record("task.schedule", System.nanoTime() - started, false,
                    Map.of("doc_id", docId, "reason", "already_running"));
            return false;
        }
        try {
            Map<String, Object> document = repo.doc(docId);
            repo.enqueueIndexTask(docId, (int) number(document.get("version"), 1L), "UPSERT");
            repo.status(docId, "PENDING", "PENDING", 0, null);
            publish(job, ProcessingEvent.progress(docId, "PENDING", "PENDING", 0, 0, 0, "文档处理任务已创建"));
            executor.execute(() -> process(job, kbId, objectKey));
            observability.record("task.schedule", System.nanoTime() - started, true,
                    Map.of("doc_id", docId, "kb_id", kbId, "version", number(document.get("version"), 1L)));
            return true;
        } catch (RuntimeException exception) {
            job.running.set(false);
            jobs.remove(docId, job);
            repo.status(docId, "FAILED", "FAILED", 0, "文档处理任务启动失败，请稍后重试");
            observability.record("task.schedule", System.nanoTime() - started, false,
                    Map.of("doc_id", docId, "error_type", exception.getClass().getSimpleName()));
            throw exception;
        }
    }

    @Scheduled(initialDelayString = "${rag.index-task.initial-delay-ms:15000}",
            fixedDelayString = "${rag.index-task.poll-ms:30000}")
    public void recoverIndexTasks() {
        long started = System.nanoTime();
        try {
            int reset = repo.resetStaleIndexTasks(10);
            int submitted = 0;
            for (Map<String, Object> task : repo.dueIndexTasks(8)) {
                long docId = number(task.get("docId"), 0L);
                long kbId = number(task.get("kbId"), 0L);
                String objectKey = String.valueOf(task.getOrDefault("objectKey", ""));
                if (docId > 0 && kbId > 0 && !objectKey.isBlank() && start(docId, kbId, objectKey)) submitted++;
            }
            observability.record("task.recovery", System.nanoTime() - started, true,
                    Map.of("reset_count", reset, "submitted_count", submitted));
        } catch (Exception failure) {
            observability.record("task.recovery", System.nanoTime() - started, false,
                    Map.of("error_type", failure.getClass().getSimpleName()));
            log.warn("Index task recovery cycle failed", failure);
        }
    }

    /**
     * Poll MinIO metadata so externally replaced objects enter the same
     * versioned processing pipeline as newly uploaded files.
     */
    @Scheduled(initialDelayString = "${rag.document-scan.initial-delay-ms:30000}",
            fixedDelayString = "${rag.document-scan.poll-ms:60000}")
    public void scanDocumentChanges() {
        long started = System.nanoTime();
        int changedCount = 0;
        try {
            for (Map<String, Object> base : repo.bases()) {
                long kbId = number(base.get("id"), 0L);
                if (kbId <= 0) continue;
                for (Map<String, Object> document : repo.docs(kbId)) {
                    long docId = number(document.get("id"), 0L);
                    String objectKey = String.valueOf(document.getOrDefault("objectKey", ""));
                    if (docId <= 0 || objectKey.isBlank()) continue;
                    ObjectStorage.ObjectInfo info = storage.stat(objectKey);
                    if (info == null) continue;
                    String etag = info.etag();
                    if (etag == null || etag.isBlank()) continue;
                    String knownEtag = String.valueOf(document.getOrDefault("objectEtag", ""));
                    long knownSize = number(document.get("fileSize"), -1L);
                    if (knownEtag.isBlank() || "null".equalsIgnoreCase(knownEtag)) {
                        repo.initializeDocumentFingerprint(docId, etag, info.size());
                        if (!"INDEXED".equalsIgnoreCase(String.valueOf(document.getOrDefault("chunkStatus", "")))
                                && !jobs.containsKey(docId)) {
                            Map<String, Object> current = repo.doc(docId);
                            int version = (int) number(current.get("version"), 1L);
                            repo.enqueueIndexTask(docId, version, "UPSERT");
                            start(docId, kbId, objectKey);
                        }
                        continue;
                    }
                    String parseStatus = String.valueOf(document.getOrDefault("parseStatus", ""));
                    String chunkStatus = String.valueOf(document.getOrDefault("chunkStatus", ""));
                    if ("PARSING".equalsIgnoreCase(parseStatus) || "INDEXING".equalsIgnoreCase(chunkStatus)) {
                        repo.touchDocumentScan(docId);
                        continue;
                    }
                    boolean changed = !knownEtag.equals(etag) || knownSize != info.size();
                    if (!changed) {
                        repo.touchDocumentScan(docId);
                        continue;
                    }
                    if (jobs.containsKey(docId)) continue;
                    if (repo.markDocumentChanged(docId, etag, info.size())) {
                        changedCount++;
                        Map<String, Object> updated = repo.doc(docId);
                        int version = (int) number(updated.get("version"), 1L);
                        repo.enqueueIndexTask(docId, version, "UPSERT");
                        start(docId, kbId, objectKey);
                        log.info("Detected changed document and queued reindex: docId={}, version={}", docId, version);
                    }
                }
            }
            observability.record("object.scan", System.nanoTime() - started, true,
                    Map.of("changed_document_count", changedCount));
        } catch (Exception failure) {
            observability.record("object.scan", System.nanoTime() - started, false,
                    Map.of("changed_document_count", changedCount, "error_type", failure.getClass().getSimpleName()));
            log.warn("Document change scan failed", failure);
        }
    }

    @Override
    public void deleteIndex(long docId) {
        ProcessingJob job = jobs.get(docId);
        if (job != null && job.running.get()) {
            throw new IllegalStateException("鏂囨。姝ｅ湪澶勭悊涓紝璇风◢鍚庡啀鍒犻櫎");
        }
        RuntimeException failure = null;
        try {
            vectorStore.deleteByDocument(docId);
        } catch (Exception cleanupFailure) {
            failure = asRuntime(cleanupFailure);
            log.error("Failed to delete document vector index: docId={}", docId, cleanupFailure);
        }
        try {
            repo.clearChunks(docId);
        } catch (Exception cleanupFailure) {
            if (failure == null) {
                failure = asRuntime(cleanupFailure);
            } else {
                failure.addSuppressed(cleanupFailure);
            }
            log.error("Failed to delete document database chunks: docId={}", docId, cleanupFailure);
        }
        if (failure != null) throw failure;
    }

    @Override
    public SseEmitter subscribe(long docId) {
        ProcessingJob job = jobs.computeIfAbsent(docId, ProcessingJob::new);
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        job.add(emitter);
        ProcessingEvent latest = job.latest;
        if (latest != null) {
            job.send(emitter, latest);
        } else {
            sendSnapshot(job, emitter);
        }
        return emitter;
    }

    private void sendSnapshot(ProcessingJob job, SseEmitter emitter) {
        try {
            Map<String, Object> document = repo.doc(job.docId);
            String parseStatus = String.valueOf(document.getOrDefault("parseStatus", "PENDING"));
            String chunkStatus = String.valueOf(document.getOrDefault("chunkStatus", "PENDING"));
            if ("INDEXED".equals(chunkStatus)) {
                job.send(emitter, ProcessingEvent.done(job.docId, document));
                close(job, emitter);
            } else if ("FAILED".equals(parseStatus) || "FAILED".equals(chunkStatus)) {
                job.send(emitter, ProcessingEvent.error(job.docId, String.valueOf(document.getOrDefault("errorMsg", "文档处理失败"))));
                close(job, emitter);
            } else {
                job.send(emitter, ProcessingEvent.progress(job.docId, parseStatus, chunkStatus, 0, 0, 0, "已连接，等待文档处理"));
            }
        } catch (Exception ignored) {
            close(job, emitter);
        }
    }

    private void process(ProcessingJob job, long kbId, String key) {
        boolean parsed = false;
        int documentVersion = 1;
        RagObservability.Span trace = observability.start("document.process", Map.of(
                "doc_id", job.docId, "kb_id", kbId, "object_key_present", !key.isBlank()));
        try {
            repo.status(job.docId, "PARSING", "PENDING", 0, null);
            documentVersion = (int) number(repo.doc(job.docId).get("version"), 1L);
            repo.updateIndexTask(job.docId, documentVersion, "UPSERT", "RUNNING", null);
            publish(job, ProcessingEvent.progress(job.docId, "PARSING", "PENDING", 5, 0, 0, "正在提取文档文本"));
            Map<String, Object> document = repo.doc(job.docId);
            Map<String, Object> base = repo.base(kbId);
            int parentTokens = normalizedParentChunkTokens(base.get("chunkSize"), PARENT_CHUNK_TOKENS);
            int parentOverlap = normalizedParentOverlapTokens(base.get("chunkOverlap"), parentTokens, 64);
            ParseTimings parseTimings = new ParseTimings();
            List<Chunker.PageChunk> parsedSources = parseChunks(document, key, parentTokens, parentOverlap, parseTimings);
            long baseSequenceStarted = System.nanoTime();
            List<Chunker.PageChunk> baseSources = withBaseSequence(parsedSources);
            parseTimings.addBaseChunk(System.nanoTime() - baseSequenceStarted);
            String parseMethod = baseSources.stream()
                    .map(chunk -> chunk.metadata() == null ? null : chunk.metadata().get("parse_method"))
                    .filter(java.util.Objects::nonNull)
                    .map(String::valueOf).findFirst()
                    .orElse(parser instanceof StructuredDocumentParser ? "docling" : parser.getClass().getSimpleName());
            observability.record("document.parse", parseTimings.parseServiceNanos(), true,
                    Map.of("doc_id", job.docId, "base_chunk_count", baseSources.size(), "version", documentVersion,
                            "parse_method", parseMethod, "timing_version", 2));
            observability.record("document.base_chunk", parseTimings.baseChunkNanos(), true,
                    Map.of("doc_id", job.docId, "base_chunk_count", baseSources.size(), "parse_method", parseMethod,
                            "timing_version", 2));
            if (baseSources.isEmpty()) {
                throw new IllegalStateException("未能从文档中提取可索引文本，请检查文件内容后重试");
            }
            long chunkStarted = System.nanoTime();
            List<ParentChunks> parents = aggregateParents(baseSources, parentTokens, parentOverlap);
            int totalChildren = parents.stream().mapToInt(parent -> parent.children().size()).sum();
            observability.record("document.parent_child_chunk", System.nanoTime() - chunkStarted, true,
                    Map.of("doc_id", job.docId, "base_chunk_count", baseSources.size(),
                            "parent_chunk_count", parents.size(), "child_chunk_count", totalChildren,
                            "parent_token_limit", parentTokens, "parent_overlap_tokens", parentOverlap,
                            "timing_version", 2));
            trace.tag("parent_count", parents.size());
            trace.tag("child_count", totalChildren);
            if (totalChildren == 0) {
                throw new IllegalStateException("No child chunks were generated");
            }
            List<Chunker.PageChunk> chunks = new ArrayList<>(totalChildren);
            parsed = true;
            repo.status(job.docId, "SUCCESS", "INDEXING", totalChildren, null);
            publish(job, ProcessingEvent.progress(job.docId, "SUCCESS", "INDEXING", 15, 0, chunks.size(), "文本提取完成，正在建立向量索引"));

            String fileName = String.valueOf(document.getOrDefault("fileName", ""));
            vectorStore.deleteByDocument(job.docId);
            repo.clearChunks(job.docId);
            // Persist layout-aware source blocks after clearing the previous index.
            long persistenceStarted = System.nanoTime();
            long databaseNanos = 0L;
            for (int baseIndex = 0; baseIndex < baseSources.size(); baseIndex++) {
                Chunker.PageChunk baseChunk = baseSources.get(baseIndex);
                Map<String, Object> metadata = tokenMetadata(baseChunk.metadata());
                String blockType = String.valueOf(metadata.getOrDefault("type", "paragraph"));
                long dbStarted = System.nanoTime();
                repo.saveBaseChunk(job.docId, kbId, baseIndex, baseChunk.content(), validPageNo(baseChunk.pageNo()),
                        chunker.countTokens(baseChunk.content()), blockType,
                        objectMapper.writeValueAsString(metadata), modelTokenizer.isApproximate());
                databaseNanos += System.nanoTime() - dbStarted;
            }
            for (int parentIndex = 0; parentIndex < parents.size(); parentIndex++) {
                ParentChunks parent = parents.get(parentIndex);
                Integer pageNo = validPageNo(parent.source().pageNo());
                String parentContent = parent.source().content();
                Map<String, Object> parentMetadata = new HashMap<>(parent.source().metadata());
                parentMetadata.put("document_version", number(document.get("version"), 1L));
                long parentDbStarted = System.nanoTime();
                long parentId = repo.saveParentChunk(job.docId, kbId, parentIndex, parentContent, pageNo,
                        chunker.countTokens(parentContent), objectMapper.writeValueAsString(parentMetadata),
                        modelTokenizer.isApproximate());
                databaseNanos += System.nanoTime() - parentDbStarted;
                int parentSeq = 0;
                for (Chunker.PageChunk child : parent.children()) {
                    String childContent = child.content();
                    Integer childPageNo = validPageNo(child.pageNo());
                    long childDbStarted = System.nanoTime();
                    long childId = repo.saveChunk(job.docId, kbId, parentId, chunks.size(), childContent, childPageNo,
                            chunker.countTokens(childContent), modelTokenizer.isApproximate());
                    databaseNanos += System.nanoTime() - childDbStarted;
                    Map<String, Object> childMetadata = tokenMetadata(parentMetadata);
                    childMetadata.put("parent_seq", parentSeq++);
                    childMetadata.put("parent_id", parentId);
                    childMetadata.put("base_chunk_seqs", childBaseSequences(parent.members(), childContent));
                    if (childPageNo != null) childMetadata.put("page_no", childPageNo);
                    long metadataDbStarted = System.nanoTime();
                    repo.updateChunkMetadata(childId, objectMapper.writeValueAsString(childMetadata));
                    databaseNanos += System.nanoTime() - metadataDbStarted;

                    long embeddingStarted = System.nanoTime();
                    List<Float> childVector = llm.embed(embeddingText(fileName, childContent));
                    observability.record("embedding.dense", System.nanoTime() - embeddingStarted, true,
                            Map.of("doc_id", job.docId, "chunk_id", childId, "parent_id", parentId,
                                    "token_count", chunker.countTokens(childContent), "vector_dimension", childVector.size()));
                    long sparseStarted = System.nanoTime();
                    SortedMap<Long, Float> childSparse = sparseVectorizer.vectorize(childContent);
                    observability.record("embedding.sparse_bm25", System.nanoTime() - sparseStarted, !childSparse.isEmpty(),
                            Map.of("doc_id", job.docId, "chunk_id", childId, "parent_id", parentId,
                                    "term_count", childSparse.size()));
                    if (childSparse.isEmpty()) throw new IllegalStateException("无法为子块生成关键词向量");
                    Map<String, Object> childPayload = new HashMap<>();
                    childPayload.put("chunk_id", childId);
                    childPayload.put("parent_id", parentId);
                    childPayload.put("doc_id", job.docId);
                    childPayload.put("kb_id", kbId);
                    childPayload.put("seq", chunks.size());
                    childPayload.put("parent_seq", parentSeq - 1);
                    childPayload.put("content", childContent);
                    childPayload.put("visibility", base.get("visibility"));
                    childPayload.put("dept_id", base.get("deptId"));
                    childPayload.put("file_name", fileName);
                    childPayload.put("chunk_level", "CHILD");
                    childPayload.put("layout_metadata", tokenMetadata(childMetadata));
                    copyPageRange(childPayload, parentMetadata);
                    childPayload.put("document_version", parentMetadata.get("document_version"));
                    if (childPageNo != null) childPayload.put("page_no", childPageNo);
                    long vectorStarted = System.nanoTime();
                    vectorStore.upsert(childId, childVector, childSparse, childPayload);
                    observability.record("vector.upsert", System.nanoTime() - vectorStarted, true,
                            Map.of("doc_id", job.docId, "chunk_id", childId, "parent_id", parentId,
                                    "payload_fields", childPayload.size()));
                    repo.updateChunkEmbeddingId(childId, String.valueOf(childId));
                    chunks.add(new Chunker.PageChunk(childContent, childPageNo, childMetadata));
                    publish(job, ProcessingEvent.segment(job.docId, childId, chunks.size() - 1,
                            childContent, chunks.size(), totalChildren,
                            15 + (int) Math.round(chunks.size() * 80D / totalChildren)));
                }
            }
            observability.record("document.persist_chunks", System.nanoTime() - persistenceStarted, true,
                    Map.of("doc_id", job.docId, "base_chunk_count", baseSources.size(), "parent_chunk_count", parents.size(), "child_chunk_count", totalChildren));
            observability.record("document.persist_database", databaseNanos, true,
                    Map.of("doc_id", job.docId, "base_chunk_count", baseSources.size(), "parent_chunk_count", parents.size(), "child_chunk_count", totalChildren));
            publish(job, ProcessingEvent.progress(job.docId, "SUCCESS", "INDEXING", 15, 0, chunks.size(),
                    "Parent and child chunks prepared"));
            repo.status(job.docId, "SUCCESS", "INDEXED", chunks.size(), null);
            repo.updateIndexTask(job.docId, documentVersion, "UPSERT", "SUCCESS", null);
            trace.success();
            publish(job, ProcessingEvent.done(job.docId, repo.doc(job.docId)));
        } catch (Exception exception) {
            String error = safeError(exception);
            log.warn("Document processing failed: docId={}, error={}", job.docId, error, exception);
            trace.failure(exception);
            observability.record("document.failure", 0, false,
                    Map.of("doc_id", job.docId, "version", documentVersion, "error_type", exception.getClass().getSimpleName()));
            cleanupPartialIndex(job.docId);
            repo.status(job.docId, parsed ? "SUCCESS" : "FAILED", "FAILED", 0, error);
            try {
                repo.updateIndexTask(job.docId, documentVersion, "UPSERT", "FAILED", error);
            } catch (Exception taskFailure) {
                log.warn("Failed to update index task status: docId={}", job.docId, taskFailure);
            }
            publish(job, ProcessingEvent.error(job.docId, error));
        } finally {
            trace.close();
            job.running.set(false);
            jobs.remove(job.docId, job);
            job.complete();
        }
    }

    private void cleanupPartialIndex(long docId) {
        try {
            vectorStore.deleteByDocument(docId);
        } catch (Exception cleanupFailure) {
            log.error("Failed to clean partial vector index: docId={}", docId, cleanupFailure);
        }
        try {
            repo.clearChunks(docId);
        } catch (Exception cleanupFailure) {
            log.error("Failed to clean partial database chunks: docId={}", docId, cleanupFailure);
        }
    }

    private static RuntimeException asRuntime(Exception failure) {
        return failure instanceof RuntimeException runtime ? runtime : new IllegalStateException(failure);
    }

    private static final class ParseTimings {
        private long parseServiceNanos;
        private long baseChunkNanos;

        private void addParseService(long durationNanos) {
            parseServiceNanos += Math.max(0L, durationNanos);
        }

        private void addBaseChunk(long durationNanos) {
            baseChunkNanos += Math.max(0L, durationNanos);
        }

        private long parseServiceNanos() {
            return parseServiceNanos;
        }

        private long baseChunkNanos() {
            return baseChunkNanos;
        }
    }

    private record ParentChunks(Chunker.PageChunk source, List<Chunker.PageChunk> children,
                                List<Chunker.PageChunk> members) {
    }

    private List<Chunker.PageChunk> withBaseSequence(List<Chunker.PageChunk> bases) {
        List<Chunker.PageChunk> sequenced = new ArrayList<>(bases.size());
        for (int index = 0; index < bases.size(); index++) {
            Chunker.PageChunk base = bases.get(index);
            Map<String, Object> metadata = new HashMap<>(base.metadata() == null ? Map.of() : base.metadata());
            metadata.put("base_chunk_seq", index);
            sequenced.add(new Chunker.PageChunk(base.content(), base.pageNo(), metadata));
        }
        return sequenced;
    }

    private List<ParentChunks> aggregateParents(List<Chunker.PageChunk> bases, int parentTokens, int parentOverlap) {
        List<ParentChunks> result = new ArrayList<>();
        List<Chunker.PageChunk> members = new ArrayList<>();
        int currentTokens = 0;
        for (Chunker.PageChunk base : bases) {
            if (base.content() == null || base.content().isBlank()) continue;
            if (!members.isEmpty() && isOverlapOnly(members.get(0))) {
                // A synthetic overlap is retained only at the beginning of a parent.
                currentTokens = members.stream().mapToInt(value -> chunker.countTokens(value.content())).sum();
            }
            String type = String.valueOf(base.metadata().getOrDefault("type", "paragraph"));
            boolean heading = "heading".equalsIgnoreCase(type) || "title".equalsIgnoreCase(type);
            int tokens = chunker.countTokens(base.content());
            // Keep short headings with the following body so they do not become
            // parent chunks that are identical to their single child chunk.
            int minimumParentTokens = Math.min(parentTokens / 2, 800);
            boolean headingBoundary = heading && currentTokens >= minimumParentTokens;
            if (!members.isEmpty() && (headingBoundary || currentTokens + tokens > parentTokens)) {
                String overlap = suffixByTokens(joinContent(members), parentOverlap);
                addParent(result, members);
                members = new ArrayList<>();
                currentTokens = 0;
                if (!overlap.isBlank()) {
                    Map<String, Object> overlapMetadata = new HashMap<>();
                    overlapMetadata.put("type", "overlap");
                    overlapMetadata.put("synthetic_overlap", true);
                    members.add(new Chunker.PageChunk(overlap,
                            members.isEmpty() ? base.pageNo() : members.get(0).pageNo(), overlapMetadata));
                    currentTokens = chunker.countTokens(overlap);
                }
            }
            members.add(base);
            currentTokens += tokens;
        }
        if (!members.isEmpty()) addParent(result, members);
        return result;
    }

    private void addParent(List<ParentChunks> result, List<Chunker.PageChunk> members) {
        String content = joinContent(members);
        if (content.isBlank()) return;
        Integer pageNo = members.get(0).pageNo();
        Map<String, Object> metadata = new HashMap<>();
        List<Chunker.PageChunk> realMembers = members.stream()
                .filter(value -> !isOverlapOnly(value)).toList();
        metadata.put("base_chunk_count", realMembers.size());
        metadata.put("base_chunk_seqs", realMembers.stream()
                .map(value -> value.metadata().get("base_chunk_seq"))
                .filter(java.util.Objects::nonNull)
                .toList());
        metadata.put("base_types", realMembers.stream()
                .map(value -> String.valueOf(value.metadata().getOrDefault("type", "paragraph")))
                .distinct().toList());
        metadata.put("token_count_estimated", modelTokenizer.isApproximate() || members.stream()
                .anyMatch(value -> Boolean.TRUE.equals(value.metadata().get("token_count_estimated"))));
        List<Integer> pages = members.stream().map(Chunker.PageChunk::pageNo)
                .filter(java.util.Objects::nonNull).toList();
        if (!pages.isEmpty()) {
            metadata.put("start_page_no", java.util.Collections.min(pages));
            metadata.put("end_page_no", java.util.Collections.max(pages));
        }
        result.add(new ParentChunks(new Chunker.PageChunk(content, pageNo, metadata),
                splitChildrenByPage(members, chunker), List.copyOf(members)));
    }

    /**
     * Split a parent without losing the page provenance of its child chunks.
     * Parents may span several pages for retrieval context, but a child should
     * never inherit the first page number of the whole parent.
     */
    static List<Chunker.PageChunk> splitChildrenByPage(List<Chunker.PageChunk> members, Chunker chunker) {
        List<Chunker.PageChunk> children = new ArrayList<>();
        List<Chunker.PageChunk> pageMembers = new ArrayList<>();
        Integer pageNo = null;
        for (Chunker.PageChunk member : members) {
            if (member.content() == null || member.content().isBlank()) continue;
            Integer memberPageNo = member.pageNo();
            if (!pageMembers.isEmpty() && !java.util.Objects.equals(pageNo, memberPageNo)) {
                appendChildren(children, pageMembers, pageNo, chunker);
                pageMembers = new ArrayList<>();
            }
            if (pageMembers.isEmpty()) pageNo = memberPageNo;
            pageMembers.add(member);
        }
        appendChildren(children, pageMembers, pageNo, chunker);
        return children;
    }

    private static void appendChildren(List<Chunker.PageChunk> target, List<Chunker.PageChunk> members,
                                       Integer pageNo, Chunker chunker) {
        if (members.isEmpty()) return;
        String content = joinContent(members);
        for (String child : chunker.splitTokens(content, CHILD_CHUNK_TOKENS, CHILD_OVERLAP_TOKENS)) {
            target.add(new Chunker.PageChunk(child, pageNo));
        }
    }

    private List<Object> childBaseSequences(List<Chunker.PageChunk> members, String child) {
        List<Object> sequences = new ArrayList<>();
        String normalizedChild = child == null ? "" : child.trim();
        for (Chunker.PageChunk member : members) {
            if (isOverlapOnly(member)) continue;
            String source = member.content() == null ? "" : member.content().trim();
            if (source.isBlank()) continue;
            String probe = source.substring(0, Math.min(32, source.length()));
            String tail = source.substring(Math.max(0, source.length() - Math.min(32, source.length())));
            if (normalizedChild.contains(probe) || normalizedChild.contains(tail)
                    || source.length() < 64 && (normalizedChild.contains(source) || source.contains(normalizedChild))) {
                Object seq = member.metadata().get("base_chunk_seq");
                if (seq != null) sequences.add(seq);
            }
        }
        return sequences;
    }

    private static String joinContent(List<Chunker.PageChunk> members) {
        return members.stream().map(Chunker.PageChunk::content)
                .filter(value -> value != null && !value.isBlank())
                .collect(java.util.stream.Collectors.joining("\n\n"));
    }

    private boolean isOverlapOnly(Chunker.PageChunk chunk) {
        return Boolean.TRUE.equals(chunk.metadata().get("synthetic_overlap"));
    }

    private void copyPageRange(Map<String, Object> target, Map<String, Object> metadata) {
        if (metadata == null) return;
        Object start = metadata.get("start_page_no");
        Object end = metadata.get("end_page_no");
        if (start != null) target.put("start_page_no", start);
        if (end != null) target.put("end_page_no", end);
    }

    private String suffixByTokens(String text, int tokenBudget) {
        if (text == null || text.isBlank() || tokenBudget <= 0) return "";
        if (chunker.countTokens(text) <= tokenBudget) return text;
        int low = 0;
        int high = text.length();
        int start = text.length();
        while (low <= high) {
            int middle = low + (high - low) / 2;
            int boundary = middle;
            if (boundary < text.length() && Character.isLowSurrogate(text.charAt(boundary))) boundary--;
            if (chunker.countTokens(text.substring(boundary)) <= tokenBudget) {
                start = boundary;
                high = boundary - 1;
            } else {
                low = boundary + 1;
            }
        }
        return text.substring(Math.max(0, start)).trim();
    }

    private List<Chunker.PageChunk> parseChunks(Map<String, Object> document, String key,
                                                int parentTokens, int parentOverlap,
                                                ParseTimings timings) throws Exception {
        String fileType = String.valueOf(document.getOrDefault("fileType", "")).toLowerCase(Locale.ROOT);
        String fileName = String.valueOf(document.getOrDefault("fileName", ""));
        boolean structuredFailed = false;
        if (parser instanceof StructuredDocumentParser structured) {
            try (InputStream input = storage.download(key)) {
                long parserStarted = System.nanoTime();
                StructuredDocumentParser.StructuredDocument parsed = structured.parseStructured(input, fileName, fileType);
                timings.addParseService(System.nanoTime() - parserStarted);
                long baseChunkStarted = System.nanoTime();
                List<Chunker.PageChunk> sampled = new ArrayList<>();
                for (StructuredDocumentParser.Block block : parsed.blocks()) {
                    if (block.text().isBlank()) continue;
                    if (block.pageNo() == null) {
                            sampled.addAll(chunker.splitTokens(block.text(), parentTokens, parentOverlap).stream()
                                .map(content -> new Chunker.PageChunk(content, null, layoutMetadata(block))).toList());
                    } else {
                            sampled.addAll(chunker.splitTokens(block.text(), parentTokens, parentOverlap).stream()
                                .map(content -> new Chunker.PageChunk(content, block.pageNo(), layoutMetadata(block))).toList());
                    }
                }
                timings.addBaseChunk(System.nanoTime() - baseChunkStarted);
                if (!sampled.isEmpty()) return sampled;
            } catch (Exception failure) {
                if (failure instanceof ScannedPdfParseException || parser instanceof DocumentParseRouter) throw failure;
                structuredFailed = true;
                log.warn("Docling 结构化采样失败，将使用兼容解析器: docId={}, error={}", document.get("id"), failure.getMessage());
            }
        }
        if (!(parser instanceof DocumentParseRouter) && "pdf".equals(fileType)) {
            try (InputStream input = storage.download(key)) {
                long parserStarted = System.nanoTime();
                List<DocParser.ParsedPage> pages = parser.parsePdfPages(input);
                timings.addParseService(System.nanoTime() - parserStarted);
                long baseChunkStarted = System.nanoTime();
                List<Chunker.PageChunk> chunks = new ArrayList<>();
                for (DocParser.ParsedPage page : pages) {
                    chunks.addAll(splitPageTokens(page.content(), page.pageNo(), parentTokens, parentOverlap));
                }
                timings.addBaseChunk(System.nanoTime() - baseChunkStarted);
                if (!chunks.isEmpty()) return chunks;
            }
        }
        try (InputStream input = storage.download(key)) {
            String text;
            if (parser instanceof DocumentParseRouter) {
                throw new IOException("Document parser returned no usable content");
            }
            long parserStarted = System.nanoTime();
            text = parser.parse(input, fileName, fileType);
            timings.addParseService(System.nanoTime() - parserStarted);
            final boolean usedStructuredFallback = structuredFailed;
            long baseChunkStarted = System.nanoTime();
            List<Chunker.PageChunk> result = chunker.splitTokens(text, parentTokens, parentOverlap).stream()
                    .map(content -> new Chunker.PageChunk(content, null, Map.of("parse_method", usedStructuredFallback ? "tika-fallback" : "tika")))
                    .toList();
            timings.addBaseChunk(System.nanoTime() - baseChunkStarted);
            return result;
        }
    }

    private List<Chunker.PageChunk> splitPageTokens(String text, int pageNo, int maxTokens, int overlapTokens) {
        return chunker.splitTokens(text, maxTokens, overlapTokens).stream()
                .map(content -> new Chunker.PageChunk(content, pageNo, Map.of("parse_method", "tika")))
                .toList();
    }

    private Map<String, Object> layoutMetadata(StructuredDocumentParser.Block block) {
        Map<String, Object> metadata = new HashMap<>(block.metadata());
        metadata.put("type", block.type());
        metadata.put("level", block.level());
        metadata.put("order", block.order());
        metadata.putIfAbsent("parser", "docling");
        metadata.putIfAbsent("parse_method", metadata.get("parser"));
        if (!block.bbox().isEmpty()) metadata.put("bbox", block.bbox());
        return metadata;
    }

    private Map<String, Object> tokenMetadata(Map<String, Object> metadata) {
        Map<String, Object> result = new HashMap<>(metadata == null ? Map.of() : metadata);
        result.put("token_count_estimated", modelTokenizer.isApproximate());
        return result;
    }

    private int normalizedParentChunkTokens(Object value, int fallback) {
        int size = value instanceof Number number ? number.intValue() : fallback;
        if (size < 128 || size > 10_000) {
            throw new IllegalArgumentException("父块 token 数必须在 128 到 10000 之间");
        }
        return size;
    }

    private int normalizedParentOverlapTokens(Object value, int chunkSize, int fallback) {
        int overlap = value instanceof Number number ? number.intValue() : fallback;
        if (overlap < 0 || overlap > chunkSize / 2) {
            throw new IllegalArgumentException("父块重叠 token 数必须在 0 到父块大小一半之间");
        }
        return overlap;
    }

    private String embeddingText(String fileName, String content) {
        String name = fileName == null ? "" : fileName.trim();
        return name.isBlank() ? content : "文件名称：" + name + "\n内容：" + content;
    }

    private void publish(ProcessingJob job, ProcessingEvent event) {
        job.latest = event;
        job.publish(event);
    }

    private Integer validPageNo(Integer pageNo) {
        return pageNo != null && pageNo > 0 && pageNo <= 100_000 ? pageNo : null;
    }

    private long number(Object value, long fallback) {
        if (value instanceof Number number) return number.longValue();
        try {
            return value == null ? fallback : Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private String safeError(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) message = "文档解析或索引失败，请检查文件后重试";
        return message.length() > 500 ? message.substring(0, 500) : message;
    }

    private void close(ProcessingJob job, SseEmitter emitter) {
        job.remove(emitter);
        try {
            emitter.complete();
        } catch (Exception ignored) {
            // The HTTP connection may already have been closed by the client.
        }
    }

    private static final class ProcessingJob {
        private final long docId;
        private final AtomicBoolean running = new AtomicBoolean(false);
        private final Set<SseEmitter> subscribers = new CopyOnWriteArraySet<>();
        private volatile ProcessingEvent latest;

        private ProcessingJob(long docId) {
            this.docId = docId;
        }

        private void add(SseEmitter emitter) {
            subscribers.add(emitter);
            emitter.onCompletion(() -> subscribers.remove(emitter));
            emitter.onTimeout(() -> subscribers.remove(emitter));
            emitter.onError(error -> subscribers.remove(emitter));
        }

        private void remove(SseEmitter emitter) {
            subscribers.remove(emitter);
        }

        private void publish(ProcessingEvent event) {
            for (SseEmitter emitter : subscribers) send(emitter, event);
        }

        private void send(SseEmitter emitter, ProcessingEvent event) {
            try {
                synchronized (emitter) {
                    emitter.send(SseEmitter.event().name(event.name).data(event.data));
                }
            } catch (IOException | IllegalStateException exception) {
                subscribers.remove(emitter);
                try {
                    emitter.complete();
                } catch (Exception ignored) {
                    // The client has already disconnected.
                }
            }
        }

        private void complete() {
            for (SseEmitter emitter : subscribers) {
                try {
                    emitter.complete();
                } catch (Exception ignored) {
                    // Completion is best effort after terminal events.
                }
            }
            subscribers.clear();
        }
    }

    private record ProcessingEvent(String name, Map<String, Object> data) {
        private static ProcessingEvent progress(long docId, String parseStatus, String chunkStatus, int progress,
                                                int completedChunks, int totalChunks, String message) {
            return new ProcessingEvent("progress", Map.of(
                    "docId", docId, "parseStatus", parseStatus, "chunkStatus", chunkStatus,
                    "progress", progress, "completedChunks", completedChunks, "totalChunks", totalChunks,
                    "message", message));
        }

        private static ProcessingEvent segment(long docId, long chunkId, int sequence, String content,
                                                int completedChunks, int totalChunks, int progress) {
            Map<String, Object> data = new HashMap<>();
            data.put("docId", docId);
            data.put("chunkId", chunkId);
            data.put("sequence", sequence);
            data.put("content", content);
            data.put("completedChunks", completedChunks);
            data.put("totalChunks", totalChunks);
            data.put("progress", progress);
            return new ProcessingEvent("segment", data);
        }

        private static ProcessingEvent done(long docId, Map<String, Object> document) {
            return new ProcessingEvent("done", Map.of(
                    "docId", docId,
                    "parseStatus", document.getOrDefault("parseStatus", "SUCCESS"),
                    "chunkStatus", document.getOrDefault("chunkStatus", "INDEXED"),
                    "chunkCount", document.getOrDefault("chunkCount", 0),
                    "message", "文档处理完成"));
        }

        private static ProcessingEvent error(long docId, String message) {
            return new ProcessingEvent("error", Map.of("docId", docId, "message", message));
        }
    }
}
