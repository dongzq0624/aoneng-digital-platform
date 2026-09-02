package com.aoneng.rag.application.processing;

import com.aoneng.rag.application.repository.PlatformRepository;
import com.aoneng.rag.infra.chunk.Chunker;
import com.aoneng.rag.infra.chunk.ModelTokenizer;
import com.aoneng.rag.infra.llm.LlmService;
import com.aoneng.rag.infra.parse.DocParser;
import com.aoneng.rag.infra.parse.StructuredDocumentParser;
import com.aoneng.rag.infra.storage.ObjectStorage;
import com.aoneng.rag.infra.vector.VectorStore;
import com.aoneng.rag.infra.vector.Bm25SparseVectorizer;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
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
    private static final int PARENT_CHUNK_TOKENS = 2_000;
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
        this.bucket = bucket;
    }

    @Override
    public boolean start(long docId, long kbId, String objectKey) {
        ProcessingJob job = jobs.computeIfAbsent(docId, ProcessingJob::new);
        if (!job.running.compareAndSet(false, true)) return false;
        try {
            repo.status(docId, "PENDING", "PENDING", 0, null);
            publish(job, ProcessingEvent.progress(docId, "PENDING", "PENDING", 0, 0, 0, "文档处理任务已创建"));
            executor.execute(() -> process(job, kbId, objectKey));
            return true;
        } catch (RuntimeException exception) {
            job.running.set(false);
            jobs.remove(docId, job);
            repo.status(docId, "FAILED", "FAILED", 0, "文档处理任务启动失败，请稍后重试");
            throw exception;
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
        try {
            repo.status(job.docId, "PARSING", "PENDING", 0, null);
            publish(job, ProcessingEvent.progress(job.docId, "PARSING", "PENDING", 5, 0, 0, "正在提取文档文本"));
            Map<String, Object> document = repo.doc(job.docId);
            Map<String, Object> base = repo.base(kbId);
            int parentTokens = normalizedParentChunkTokens(base.get("chunkSize"), PARENT_CHUNK_TOKENS);
            int parentOverlap = normalizedParentOverlapTokens(base.get("chunkOverlap"), parentTokens, 64);
            List<Chunker.PageChunk> parentSources = parseChunks(document, key, parentTokens, parentOverlap);
            if (parentSources.isEmpty()) {
                throw new IllegalStateException("未能从文档中提取可索引文本，请检查文件内容后重试");
            }
            List<ParentChunks> parents = parentSources.stream()
                    .map(source -> new ParentChunks(source,
                            chunker.splitTokens(source.content(), CHILD_CHUNK_TOKENS, CHILD_OVERLAP_TOKENS)))
                    .filter(parent -> !parent.children().isEmpty())
                    .toList();
            int totalChildren = parents.stream().mapToInt(parent -> parent.children().size()).sum();
            if (totalChildren == 0) {
                throw new IllegalStateException("No child chunks were generated");
            }
            List<Chunker.PageChunk> chunks = new ArrayList<>(totalChildren);
            List<Long> parentIds = new ArrayList<>(totalChildren);
            parsed = true;
            repo.status(job.docId, "SUCCESS", "INDEXING", totalChildren, null);
            publish(job, ProcessingEvent.progress(job.docId, "SUCCESS", "INDEXING", 15, 0, chunks.size(), "文本提取完成，正在建立向量索引"));

            String fileName = String.valueOf(document.getOrDefault("fileName", ""));
            vectorStore.deleteByDocument(job.docId);
            repo.clearChunks(job.docId);
            // Persist layout-aware source blocks after clearing the previous index.
            for (int baseIndex = 0; baseIndex < parentSources.size(); baseIndex++) {
                Chunker.PageChunk baseChunk = parentSources.get(baseIndex);
                Map<String, Object> metadata = tokenMetadata(baseChunk.metadata());
                String blockType = String.valueOf(metadata.getOrDefault("type", "paragraph"));
                repo.saveBaseChunk(job.docId, kbId, baseIndex, baseChunk.content(), validPageNo(baseChunk.pageNo()),
                        chunker.countTokens(baseChunk.content()), blockType,
                        objectMapper.writeValueAsString(metadata));
            }
            for (int parentIndex = 0; parentIndex < parents.size(); parentIndex++) {
                ParentChunks parent = parents.get(parentIndex);
                Integer pageNo = validPageNo(parent.source().pageNo());
                String parentContent = parent.source().content();
                long parentId = repo.saveParentChunk(job.docId, kbId, parentIndex, parentContent, pageNo,
                        chunker.countTokens(parentContent));
                List<Float> parentVector = llm.embed(embeddingText(fileName, parentContent));
                SortedMap<Long, Float> parentSparse = sparseVectorizer.vectorize(parentContent);
                if (parentSparse.isEmpty()) throw new IllegalStateException("无法为父块生成关键词向量");
                Map<String, Object> parentPayload = new HashMap<>();
                parentPayload.put("chunk_id", parentId);
                parentPayload.put("parent_id", parentId);
                parentPayload.put("doc_id", job.docId);
                parentPayload.put("kb_id", kbId);
                parentPayload.put("seq", parentIndex);
                parentPayload.put("content", parentContent);
                parentPayload.put("visibility", base.get("visibility"));
                parentPayload.put("dept_id", base.get("deptId"));
                parentPayload.put("file_name", fileName);
                parentPayload.put("chunk_level", "PARENT");
                parentPayload.put("layout_metadata", tokenMetadata(parent.source().metadata()));
                if (pageNo != null) parentPayload.put("page_no", pageNo);
                vectorStore.upsert(parentId, parentVector, parentSparse, parentPayload);
                repo.updateParentEmbeddingId(parentId, String.valueOf(parentId));
                for (String child : parent.children()) {
                    long childId = repo.saveChunk(job.docId, kbId, parentId, chunks.size(), child, pageNo,
                            chunker.countTokens(child));
                    Map<String, Object> childMetadata = tokenMetadata(parent.source().metadata());
                    repo.updateChunkMetadata(childId, objectMapper.writeValueAsString(childMetadata));
                    chunks.add(new Chunker.PageChunk(child, pageNo, childMetadata));
                    parentIds.add(parentId);
                    publish(job, ProcessingEvent.segment(job.docId, childId, chunks.size() - 1, pageNo,
                            child, chunks.size(), totalChildren,
                            15 + (int) Math.round(chunks.size() * 80D / totalChildren)));
                }
            }
            publish(job, ProcessingEvent.progress(job.docId, "SUCCESS", "INDEXING", 15, 0, chunks.size(),
                    "Parent and child chunks prepared"));
            // Child chunks are persisted above; only parent chunks are indexed in Milvus.
            for (int index = chunks.size(); index < chunks.size(); index++) {
                Chunker.PageChunk chunk = chunks.get(index);
                Integer pageNo = validPageNo(chunk.pageNo());
                List<Float> vector = llm.embed(embeddingText(fileName, chunk.content()));
                SortedMap<Long, Float> sparseVector = sparseVectorizer.vectorize(chunk.content());
                long parentId = parentIds.get(index);
                long chunkId = repo.saveChunk(job.docId, kbId, parentId, index, chunk.content(), pageNo,
                        chunker.countTokens(chunk.content()));
                Map<String, Object> payload = new HashMap<>();
                payload.put("chunk_id", chunkId);
                payload.put("parent_id", parentId);
                payload.put("doc_id", job.docId);
                payload.put("kb_id", kbId);
                payload.put("seq", index);
                payload.put("content", chunk.content());
                payload.put("visibility", base.get("visibility"));
                payload.put("dept_id", base.get("deptId"));
                payload.put("file_name", fileName);
                payload.put("chunk_level", "CHILD");
                payload.put("layout_metadata", chunk.metadata());
                if (pageNo != null) payload.put("page_no", pageNo);
                if (sparseVector.isEmpty()) {
                    throw new IllegalStateException("无法为文档分块生成关键词向量");
                }
                vectorStore.upsert(chunkId, vector, sparseVector, payload);
                repo.updateChunkMetadata(chunkId, objectMapper.writeValueAsString(chunk.metadata()));
                // Milvus uses the PostgreSQL chunk ID as its primary key; persist the same
                // identifier only after the external write succeeds.
                repo.updateChunkEmbeddingId(chunkId, String.valueOf(chunkId));

                int completed = index + 1;
                int progress = 15 + (int) Math.round(completed * 80D / chunks.size());
                publish(job, ProcessingEvent.segment(job.docId, chunkId, index, pageNo,
                        chunk.content(), completed, chunks.size(), progress));
            }
            repo.status(job.docId, "SUCCESS", "INDEXED", chunks.size(), null);
            publish(job, ProcessingEvent.done(job.docId, repo.doc(job.docId)));
        } catch (Exception exception) {
            String error = safeError(exception);
            log.warn("Document processing failed: docId={}, error={}", job.docId, error, exception);
            cleanupPartialIndex(job.docId);
            repo.status(job.docId, parsed ? "SUCCESS" : "FAILED", "FAILED", 0, error);
            publish(job, ProcessingEvent.error(job.docId, error));
        } finally {
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

    private record ParentChunks(Chunker.PageChunk source, List<String> children) {
    }

    private List<Chunker.PageChunk> parseChunks(Map<String, Object> document, String key,
                                                int parentTokens, int parentOverlap) throws Exception {
        String fileType = String.valueOf(document.getOrDefault("fileType", "")).toLowerCase(Locale.ROOT);
        String fileName = String.valueOf(document.getOrDefault("fileName", ""));
        if (parser instanceof StructuredDocumentParser structured) {
            try (InputStream input = storage.download(key)) {
                StructuredDocumentParser.StructuredDocument parsed = structured.parseStructured(input, fileName, fileType);
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
                if (!sampled.isEmpty()) return sampled;
            } catch (Exception failure) {
                log.warn("Docling 结构化采样失败，将使用兼容解析器: docId={}, error={}", document.get("id"), failure.getMessage());
            }
        }
        if ("pdf".equals(fileType)) {
            try (InputStream input = storage.download(key)) {
                List<Chunker.PageChunk> chunks = new ArrayList<>();
                for (DocParser.ParsedPage page : parser.parsePdfPages(input)) {
                    chunks.addAll(splitPageTokens(page.content(), page.pageNo(), parentTokens, parentOverlap));
                }
                if (!chunks.isEmpty()) return chunks;
            }
        }
        try (InputStream input = storage.download(key)) {
            String text = parser.parse(input, fileName, fileType);
            return chunker.splitTokens(text, parentTokens, parentOverlap).stream()
                    .map(content -> new Chunker.PageChunk(content, null))
                    .toList();
        }
    }

    private List<Chunker.PageChunk> splitPageTokens(String text, int pageNo, int maxTokens, int overlapTokens) {
        return chunker.splitTokens(text, maxTokens, overlapTokens).stream()
                .map(content -> new Chunker.PageChunk(content, pageNo))
                .toList();
    }

    private Map<String, Object> layoutMetadata(StructuredDocumentParser.Block block) {
        Map<String, Object> metadata = new HashMap<>(block.metadata());
        metadata.put("type", block.type());
        metadata.put("level", block.level());
        metadata.put("order", block.order());
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

        private static ProcessingEvent segment(long docId, long chunkId, int sequence, Integer pageNo, String content,
                                               int completedChunks, int totalChunks, int progress) {
            Map<String, Object> data = new HashMap<>();
            data.put("docId", docId);
            data.put("chunkId", chunkId);
            data.put("sequence", sequence);
            data.put("content", content);
            data.put("completedChunks", completedChunks);
            data.put("totalChunks", totalChunks);
            data.put("progress", progress);
            if (pageNo != null) data.put("pageNo", pageNo);
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
