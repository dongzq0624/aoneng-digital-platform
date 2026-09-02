package com.aoneng.rag.infra.chunk;

import java.util.List;
import java.util.Map;

/**
 * 文本分块服务接口。
 * 将长文本切分为固定大小的重叠块，支持按页切分。
 */
public interface Chunker {

    /**
     * 将纯文本切分为固定大小的重叠块。
     *
     * @param text     待切分的文本
     * Legacy character-based splitter retained for compatibility.
     * @return 分块文本列表
     */
    List<String> split(String text, int maxChars, int overlap);

    /** Split text using the configured model tokenizer's token budget. */
    List<String> splitTokens(String text, int maxTokens, int overlapTokens);

    /** Count tokens with the configured model tokenizer. */
    int countTokens(String text);

    /**
     * 将带页码的文本切分为重叠的分块。
     *
     * @param text     待切分的文本
     * @param pageNo   页码
     * Legacy character-based page splitter retained for compatibility.
     * @return 带页码的分块列表
     */
    List<PageChunk> splitPage(String text, int pageNo, int maxChars, int overlap);

    /**
     * 带页码的分块记录。
     *
     * @param content 分块文本内容
     * @param pageNo  页码（可为 null）
     */
    record PageChunk(String content, Integer pageNo, Map<String, Object> metadata) {
        public PageChunk(String content, Integer pageNo) {
            this(content, pageNo, Map.of());
        }
        public PageChunk {
            metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        }
    }
}
