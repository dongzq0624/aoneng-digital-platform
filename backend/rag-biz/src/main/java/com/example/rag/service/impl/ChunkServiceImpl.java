package com.example.rag.service.impl;

import com.example.rag.service.ChunkService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 分块服务默认实现。
 * 基于字符切分文本，优先在句子/段落边界处断开，并支持可配置的重叠。
 */
@Service
public class ChunkServiceImpl implements ChunkService {

    /**
     * 将纯文本切分为固定大小的重叠块。
     * 优先在换行符或句末标点处断开，确保分块的语义完整性。
     *
     * @param text     待切分的文本
     * @param maxChars 每块最大字符数（最小 128）
     * @param overlap  相邻块之间的重叠字符数（最大不超过 size 的一半）
     * @return 分块文本列表
     */
    @Override
    public List<String> split(String text, int maxChars, int overlap) {
        if (text == null || text.isBlank()) return List.of();
        int size = Math.max(128, maxChars);
        int safeOverlap = Math.max(0, Math.min(overlap, size / 2));
        List<String> chunks = new ArrayList<>();
        String normalized = text.replace("\r\n", "\n").trim();
        int start = 0;
        while (start < normalized.length()) {
            int maximumEnd = Math.min(normalized.length(), start + size);
            int end = maximumEnd == normalized.length()
                    ? maximumEnd
                    : preferredBoundary(normalized, start, maximumEnd);
            if (end <= start) end = maximumEnd;
            String chunk = normalized.substring(start, end).trim();
            if (!chunk.isBlank()) chunks.add(chunk);
            if (end >= normalized.length()) break;
            start = Math.max(end - safeOverlap, start + 1);
        }
        return chunks;
    }

    /**
     * 在 [minimumEnd, maximumEnd) 范围内寻找最佳分块边界。
     * 优先匹配换行符、句末标点；其次匹配空白或逗号；最后回退到 maximumEnd。
     */
    private int preferredBoundary(String text, int start, int maximumEnd) {
        int minimumEnd = Math.min(maximumEnd, start + Math.max(64, (maximumEnd - start) / 2));
        for (int index = maximumEnd - 1; index >= minimumEnd; index--) {
            char current = text.charAt(index);
            if (current == '\n' || current == '.' || current == '!' || current == '?') {
                return index + 1;
            }
        }
        for (int index = maximumEnd - 1; index >= minimumEnd; index--) {
            if (Character.isWhitespace(text.charAt(index)) || text.charAt(index) == ',') {
                return index + 1;
            }
        }
        return maximumEnd;
    }

    /**
     * 将带页码的文本切分为重叠的分块，每块关联指定的页码。
     */
    @Override
    public List<PageChunk> splitPage(String text, int pageNo, int maxChars, int overlap) {
        return split(text, maxChars, overlap).stream()
                .map(content -> new PageChunk(content, pageNo))
                .toList();
    }
}
