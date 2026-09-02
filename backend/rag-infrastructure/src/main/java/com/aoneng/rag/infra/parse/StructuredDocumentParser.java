package com.aoneng.rag.infra.parse;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

/** Parser contract exposing structure needed by chunking and citation generation. */
public interface StructuredDocumentParser extends DocParser {
    StructuredDocument parseStructured(InputStream input, String fileName, String extension)
            throws IOException;

    record StructuredDocument(String text, List<Block> blocks, Map<String, Object> metadata) {
        public StructuredDocument {
            text = text == null ? "" : text;
            blocks = blocks == null ? List.of() : List.copyOf(blocks);
            metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        }
    }

    record Block(String type, String text, Integer pageNo, Integer level, Integer order,
                 Map<String, Object> bbox, Map<String, Object> metadata) {
        public Block {
            type = type == null || type.isBlank() ? "paragraph" : type;
            text = text == null ? "" : text;
            level = level == null ? 0 : Math.max(0, level);
            order = order == null ? 0 : Math.max(0, order);
            bbox = bbox == null ? Map.of() : Map.copyOf(bbox);
            metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        }
    }
}
