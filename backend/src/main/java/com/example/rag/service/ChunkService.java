package com.example.rag.service;

import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class ChunkService {
    public record PageChunk(String content, Integer pageNo) { }

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

    private int preferredBoundary(String text, int start, int maximumEnd) {
        int minimumEnd = Math.min(maximumEnd, start + Math.max(64, (maximumEnd - start) / 2));
        for (int index = maximumEnd - 1; index >= minimumEnd; index--) {
            char current = text.charAt(index);
            if (current == '\n' || current == '。' || current == '！' || current == '？'
                    || current == '；' || current == '.' || current == '!' || current == '?') {
                return index + 1;
            }
        }
        for (int index = maximumEnd - 1; index >= minimumEnd; index--) {
            if (Character.isWhitespace(text.charAt(index)) || text.charAt(index) == '，' || text.charAt(index) == ',') {
                return index + 1;
            }
        }
        return maximumEnd;
    }

    public List<PageChunk> splitPage(String text, int pageNo, int maxChars, int overlap) {
        return split(text, maxChars, overlap).stream().map(content -> new PageChunk(content, pageNo)).toList();
    }
}
