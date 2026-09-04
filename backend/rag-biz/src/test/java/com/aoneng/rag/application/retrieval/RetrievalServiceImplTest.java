package com.aoneng.rag.application.retrieval;

import com.aoneng.rag.application.repository.PlatformRepository;
import com.aoneng.rag.infra.llm.LlmService;
import com.aoneng.rag.infra.vector.Bm25SparseVectorizer;
import com.aoneng.rag.infra.vector.VectorStore;
import com.aoneng.rag.observability.RagObservability;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RetrievalServiceImplTest {

    @Test
    void childHitsAreDeduplicatedByParentAndRerankedWithParentContent() {
        LlmService llm = mock(LlmService.class);
        PlatformRepository repo = mock(PlatformRepository.class);
        VectorStore vectorStore = mock(VectorStore.class);
        Bm25SparseVectorizer sparse = mock(Bm25SparseVectorizer.class);
        RagObservability observability = mock(RagObservability.class);
        when(llm.embed(anyString())).thenReturn(List.of(0.1F));
        when(sparse.vectorize(anyString())).thenReturn(new TreeMap<>(Map.of(1L, 1F)));
        Map<String, Object> parent = Map.of("id", 10L, "docId", 1L, "kbId", 2L,
                "content", "父块完整上下文");
        when(repo.parentChunk(10L)).thenReturn(parent);
        when(vectorStore.searchAdaptive(anyList(), eq(50), eq(List.of(2L)))).thenReturn(List.of(
                hit(101L, 0.95D, Map.of("chunk_id", 101L, "parent_id", 10L, "doc_id", 1L,
                        "kb_id", 2L, "content", "子块一", "chunk_level", "CHILD", "page_no", 1)),
                hit(102L, 0.90D, Map.of("chunk_id", 102L, "parent_id", 10L, "doc_id", 1L,
                        "kb_id", 2L, "content", "子块二", "chunk_level", "CHILD", "page_no", 2))));
        when(vectorStore.sparseSearch(any(SortedMap.class), eq(50), eq(List.of(2L)))).thenReturn(List.of());
        when(llm.rerank(anyString(), anyList(), eq(5)))
                .thenReturn(List.of(new LlmService.RerankResult(0, 0.99D)));

        RetrievalServiceImpl service = new RetrievalServiceImpl(llm, repo, vectorStore, sparse,
                false, true, 0, 50, 50, 50, 5, 60, observability);

        RetrievalService.RetrievalResult result = service.retrieve("问题", List.of(2L));

        assertEquals(1, result.hits().size());
        Map<String, Object> payload = payload(result.hits().get(0));
        assertEquals(101L, number(payload.get("chunk_id")));
        assertEquals(10L, number(payload.get("parent_id")));
        assertEquals(0.99D, ((Number) result.hits().get(0).get("score")).doubleValue());
        verify(repo).parentChunk(10L);
        verify(llm).rerank(eq("问题"), eq(List.of("父块完整上下文")), eq(5));
    }

    @Test
    void legacyParentPointsRemainSearchable() {
        LlmService llm = mock(LlmService.class);
        PlatformRepository repo = mock(PlatformRepository.class);
        VectorStore vectorStore = mock(VectorStore.class);
        Bm25SparseVectorizer sparse = mock(Bm25SparseVectorizer.class);
        RagObservability observability = mock(RagObservability.class);
        when(llm.embed(anyString())).thenReturn(List.of(0.1F));
        when(sparse.vectorize(anyString())).thenReturn(new TreeMap<>());
        when(vectorStore.searchAdaptive(anyList(), eq(50), eq(List.of(2L)))).thenReturn(List.of(
                hit(10L, 0.80D, Map.of("chunk_id", 10L, "parent_id", 10L, "doc_id", 1L,
                        "kb_id", 2L, "content", "历史父块", "chunk_level", "PARENT"))));
        when(vectorStore.sparseSearch(any(SortedMap.class), eq(50), eq(List.of(2L)))).thenReturn(List.of());
        when(llm.rerank(anyString(), anyList(), eq(5)))
                .thenReturn(List.of(new LlmService.RerankResult(0, 0.80D)));

        RetrievalServiceImpl service = new RetrievalServiceImpl(llm, repo, vectorStore, sparse,
                false, true, 0, 50, 50, 50, 5, 60, observability);

        RetrievalService.RetrievalResult result = service.retrieve("问题", List.of(2L));

        assertEquals(1, result.hits().size());
        assertTrue(payload(result.hits().get(0)).get("content").equals("历史父块"));
        verify(repo, never()).parentChunk(anyLong());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> payload(Map<String, Object> hit) {
        return (Map<String, Object>) hit.get("payload");
    }

    private static Map<String, Object> hit(long id, double score, Map<String, Object> payload) {
        return Map.of("id", id, "score", score, "payload", payload);
    }

    private static long number(Object value) {
        return value instanceof Number number ? number.longValue() : Long.parseLong(String.valueOf(value));
    }
}
