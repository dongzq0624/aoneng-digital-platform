package com.example.rag.service;

import com.example.rag.kb.service.ChunkService;
import com.example.rag.kb.service.DocParseService;
import com.example.rag.kb.service.DocumentProcessingService;
import com.example.rag.chat.service.QdrantService;
import com.example.rag.service.DashScopeService;
import com.example.rag.service.PlatformRepository;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 异步文档处理服务默认实现。
 * 编排解析 + 分块 + 嵌入 + 向量写入的异步流水线。
 * 在专用线程池中运行每个任务，并通过 SSE 流式传输进度事件给订阅者。
 *
 * <p>状态保存在两个内存映射中；任务到达终态后从注册表中移除。</p>
 */
@Service
public class DocumentProcessingServiceImpl implements DocumentProcessingService {

    private static final Logger log = LoggerFactory.getLogger(DocumentProcessingServiceImpl.class);
    private static final long SSE_TIMEOUT_MS = 0L;

    private final PlatformRepository repo;
    private final MinioClient minio;
    private final DocParseService parser;
    private final ChunkService chunker;
    private final DashScopeService dash;
    private final QdrantService qdrant;
    private final TaskExecutor executor;
    private final String bucket;
    private final Map<Long, ProcessingJob> jobs = new ConcurrentHashMap<>();

    public DocumentProcessingServiceImpl(PlatformRepository repo,
                                         MinioClient minio,
                                         DocParseService parser,
                                         ChunkService chunker,
                                         DashScopeService dash,
                                         QdrantService qdrant,
                                         @Qualifier("documentProcessingExecutor") TaskExecutor executor,
                                         @Value("${minio.bucket:rag-docs}") String bucket) {
        this.repo = repo;
        this.minio = minio;
        this.parser = parser;
        this.chunker = chunker;
        this.dash = dash;
        this.qdrant = qdrant;
        this.executor = executor;
        this.bucket = bucket;
    }

    /**
     * 启动文档处理任务。
     */
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

    /**
     * 订阅文档处理进度事件。
     */
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
            List<ChunkService.PageChunk> chunks = parseChunks(document, key);
            if (chunks.isEmpty()) {
                throw new IllegalStateException("未能从文档中提取可索引文本，请检查文件内容后重试");
            }
            parsed = true;
            repo.status(job.docId, "SUCCESS", "INDEXING", chunks.size(), null);
            publish(job, ProcessingEvent.progress(job.docId, "SUCCESS", "INDEXING", 15, 0, chunks.size(), "文本提取完成，正在建立向量索引"));

            Map<String, Object> base = repo.base(kbId);
            String fileName = String.valueOf(document.getOrDefault("fileName", ""));
            qdrant.deleteByDocument(job.docId);
            repo.clearChunks(job.docId);
            for (int index = 0; index < chunks.size(); index++) {
                ChunkService.PageChunk chunk = chunks.get(index);
                Integer pageNo = validPageNo(chunk.pageNo());
                List<Float> vector = dash.embed(embeddingText(fileName, chunk.content()));
                long chunkId = repo.saveChunk(job.docId, kbId, index, chunk.content(), pageNo, vector);
                Map<String, Object> payload = new HashMap<>();
                payload.put("chunk_id", chunkId);
                payload.put("doc_id", job.docId);
                payload.put("kb_id", kbId);
                payload.put("seq", index);
                payload.put("content", chunk.content());
                payload.put("visibility", base.get("visibility"));
                payload.put("dept_id", base.get("deptId"));
                payload.put("file_name", fileName);
                if (pageNo != null) payload.put("page_no", pageNo);
                qdrant.upsert(chunkId, vector, payload);

                int completed = index + 1;
                int progress = 15 + (int) Math.round(completed * 80D / chunks.size());
                publish(job, ProcessingEvent.segment(job.docId, chunkId, index, pageNo, chunk.content(), completed, chunks.size(), progress));
            }
            repo.status(job.docId, "SUCCESS", "INDEXED", chunks.size(), null);
            publish(job, ProcessingEvent.done(job.docId, repo.doc(job.docId)));
        } catch (Exception exception) {
            String error = safeError(exception);
            log.warn("Document processing failed: docId={}, error={}", job.docId, error, exception);
            repo.status(job.docId, parsed ? "SUCCESS" : "FAILED", "FAILED", 0, error);
            publish(job, ProcessingEvent.error(job.docId, error));
        } finally {
            job.running.set(false);
            jobs.remove(job.docId, job);
            job.complete();
        }
    }

    private List<ChunkService.PageChunk> parseChunks(Map<String, Object> document, String key) throws Exception {
        String fileType = String.valueOf(document.getOrDefault("fileType", "")).toLowerCase(Locale.ROOT);
        String fileName = String.valueOf(document.getOrDefault("fileName", ""));
        if ("pdf".equals(fileType)) {
            try (InputStream input = minio.getObject(GetObjectArgs.builder().bucket(bucket).object(key).build())) {
                List<ChunkService.PageChunk> chunks = new ArrayList<>();
                Map<String, Object> base = repo.base(((Number) document.get("kbId")).longValue());
                int chunkSize = normalizedChunkSize(base.get("chunkSize"));
                int chunkOverlap = normalizedChunkOverlap(base.get("chunkOverlap"), chunkSize);
                for (DocParseService.ParsedPage page : parser.parsePdfPages(input)) {
                    chunks.addAll(chunker.splitPage(page.content(), page.pageNo(), chunkSize, chunkOverlap));
                }
                if (!chunks.isEmpty()) return chunks;
            }
        }
        try (InputStream input = minio.getObject(GetObjectArgs.builder().bucket(bucket).object(key).build())) {
            String text = parser.parse(input, fileName, fileType);
            Map<String, Object> base = repo.base(((Number) document.get("kbId")).longValue());
            int chunkSize = normalizedChunkSize(base.get("chunkSize"));
            int chunkOverlap = normalizedChunkOverlap(base.get("chunkOverlap"), chunkSize);
            return chunker.split(text, chunkSize, chunkOverlap).stream()
                    .map(content -> new ChunkService.PageChunk(content, null))
                    .toList();
        }
    }

    private int normalizedChunkSize(Object value) {
        int size = value instanceof Number number ? number.intValue() : 512;
        return Math.max(128, Math.min(size, 2_000));
    }

    private int normalizedChunkOverlap(Object value, int chunkSize) {
        int overlap = value instanceof Number number ? number.intValue() : 64;
        return Math.max(0, Math.min(overlap, chunkSize / 2));
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
