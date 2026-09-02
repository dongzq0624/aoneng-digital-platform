package com.aoneng.rag.infra.vector;

import org.junit.jupiter.api.Test;

import java.util.SortedMap;

import static org.junit.jupiter.api.Assertions.*;

class Bm25SparseVectorizerTest {

    private final Bm25SparseVectorizer vectorizer = new Bm25SparseVectorizer();

    @Test
    void producesDeterministicNonEmptySparseVector() {
        SortedMap<Long, Float> first = vectorizer.vectorize("Milvus 混合检索 Milvus");
        SortedMap<Long, Float> second = vectorizer.vectorize("Milvus 混合检索 Milvus");

        assertFalse(first.isEmpty());
        assertEquals(first, second);
        assertTrue(first.values().stream().allMatch(value -> value > 0));
    }

    @Test
    void ignoresBlankInput() {
        assertTrue(vectorizer.vectorize(" \n\t ").isEmpty());
    }
}
