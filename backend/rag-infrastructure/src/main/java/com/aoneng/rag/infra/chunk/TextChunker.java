package com.aoneng.rag.infra.chunk;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 文本分块服务默认实现。
 * 基于字符切分文本，优先在句子/段落边界处断开，并支持可配置的重叠。
 */
@Component
public class TextChunker implements Chunker {

    private final ModelTokenizer tokenizer;

    public TextChunker(ModelTokenizer tokenizer) {
        this.tokenizer = tokenizer;
    }

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

    @Override
    public List<String> splitTokens(String text, int maxTokens, int overlapTokens) {
        if (text == null || text.isBlank()) return List.of();
        int budget = Math.max(1, maxTokens);
        int overlap = Math.max(0, Math.min(overlapTokens, budget / 2));
        String normalized = text.replace("\r\n", "\n").trim();
        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < normalized.length()) {
            int end = endForTokens(normalized, start, budget);
            if (end < normalized.length()) {
                int boundary = preferredBoundary(normalized, start, end);
                if (countTokens(normalized.substring(start, boundary)) <= budget) end = boundary;
            }
            if (end <= start) end = Math.min(normalized.length(), start + 1);
            String chunk = normalized.substring(start, end).trim();
            if (!chunk.isBlank()) chunks.add(chunk);
            if (end >= normalized.length()) break;
            if (overlap == 0) {
                start = end;
                continue;
            }
            int next = end;
            while (next > start) {
                int previous = normalized.offsetByCodePoints(next, -1);
                if (countTokens(normalized.substring(previous, end)) > overlap) break;
                next = previous;
            }
            start = Math.max(end - 1, next);
        }
        return chunks;
    }

    @Override
    public int countTokens(String text) {
        return tokenizer.count(text);
    }

    private int endForTokens(String text, int start, int budget) {
        int low = start + 1;
        int high = text.length();
        int best = low;
        while (low <= high) {
            int middle = low + (high - low) / 2;
            int boundary = middle;
            if (middle < text.length() && Character.isLowSurrogate(text.charAt(middle))) boundary--;
            if (countTokens(text.substring(start, boundary)) <= budget) {
                best = boundary;
                low = boundary + 1;
            } else {
                high = boundary - 1;
            }
        }
        return best;
    }

    @Override
    public List<PageChunk> splitPage(String text, int pageNo, int maxChars, int overlap) {
        return split(text, maxChars, overlap).stream()
                .map(content -> new PageChunk(content, pageNo))
                .toList();
    }

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
}
