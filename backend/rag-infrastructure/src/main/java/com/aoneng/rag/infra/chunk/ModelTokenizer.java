package com.aoneng.rag.infra.chunk;

import ai.djl.huggingface.tokenizers.Encoding;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

/** Offline Qwen3-Embedding tokenizer loader with an explicit approximation fallback. */
@Component
public final class ModelTokenizer {
    private final String path;
    private volatile HuggingFaceTokenizer tokenizer;
    private volatile boolean approximate;
    private final AtomicBoolean warningLogged = new AtomicBoolean();

    public ModelTokenizer(@Value("${rag.tokenizer.path:/models/qwen3-embedding-tokenizer/tokenizer.json}") String path) {
        this.path = path == null ? "" : path.trim();
    }

    @PostConstruct
    void validateAtStartup() {
        if (path.isBlank() || !Files.isRegularFile(Path.of(path))) {
            approximate = true;
            warn("离线 tokenizer 文件不存在，将使用近似 token 估算: " + path);
        }
    }

    public int count(String text) {
        if (text == null || text.isBlank()) return 0;
        if (approximate) return approximateCount(text);
        try {
            HuggingFaceTokenizer loaded = tokenizer();
            if (loaded == null) return approximateCount(text);
            Encoding encoding = loaded.encode(text, false, false);
            return encoding.getIds().length;
        } catch (RuntimeException failure) {
            approximate = true;
            warn("离线 tokenizer 加载异常，将使用近似 token 估算");
            return approximateCount(text);
        }
    }

    public boolean isApproximate() {
        return approximate;
    }

    private HuggingFaceTokenizer tokenizer() {
        HuggingFaceTokenizer current = tokenizer;
        if (current != null) return current;
        synchronized (this) {
            current = tokenizer;
            if (current != null) return current;
            if (path.isBlank() || !Files.isRegularFile(Path.of(path))) {
                approximate = true;
                return null;
            }
            try {
                // DJL otherwise inherits the model's 512-token maximum and silently
                // truncates long documents. Counting must see the complete input;
                // chunk budgets are enforced by TextChunker instead.
                current = HuggingFaceTokenizer.builder()
                        .optTokenizerPath(Path.of(path))
                        .optTruncation(false)
                        .build();
                tokenizer = current;
                return current;
            } catch (Exception failure) {
                approximate = true;
                warn("离线 tokenizer 加载异常，将使用近似 token 估算");
                return null;
            }
        }
    }

    private int approximateCount(String text) {
        int count = 0;
        for (int i = 0; i < text.length();) {
            int codePoint = text.codePointAt(i);
            count++;
            i += Character.charCount(codePoint);
        }
        return Math.max(1, count);
    }

    private void warn(String message) {
        if (warningLogged.compareAndSet(false, true)) {
            org.slf4j.LoggerFactory.getLogger(ModelTokenizer.class).warn(message);
        }
    }

    @PreDestroy
    public void close() {
        HuggingFaceTokenizer current = tokenizer;
        if (current != null) current.close();
    }
}
