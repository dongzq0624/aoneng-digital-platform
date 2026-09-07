package com.aoneng.rag.doc.vo;

import java.util.List;

/** Parent chunk content for a document detail view. */
public record DocumentParentChunksVO(String fileName, List<ParentChunkVO> items) {
    public record ParentChunkVO(int sequence, String content, Integer tokenCount) { }
}
