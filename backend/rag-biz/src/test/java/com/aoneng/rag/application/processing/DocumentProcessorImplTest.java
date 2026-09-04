package com.aoneng.rag.application.processing;

import com.aoneng.rag.infra.chunk.Chunker;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DocumentProcessorImplTest {

    @Test
    void childChunksKeepPageNumberWhenParentSpansSeveralPages() {
        Chunker chunker = mock(Chunker.class);
        when(chunker.splitTokens(anyString(), eq(400), eq(64)))
                .thenAnswer(invocation -> List.of(invocation.getArgument(0, String.class)));

        List<Chunker.PageChunk> members = List.of(
                new Chunker.PageChunk("page one heading", 1),
                new Chunker.PageChunk("page one body", 1),
                new Chunker.PageChunk("page two body", 2),
                new Chunker.PageChunk("page three body", 3));

        List<Chunker.PageChunk> children = DocumentProcessorImpl.splitChildrenByPage(members, chunker);

        assertEquals(List.of(1, 2, 3), children.stream().map(Chunker.PageChunk::pageNo).toList());
        assertEquals("page one heading\n\npage one body", children.get(0).content());
    }

    @Test
    void childChunksRemainPageLessWhenSourceHasNoPageNumbers() {
        Chunker chunker = mock(Chunker.class);
        when(chunker.splitTokens(anyString(), eq(400), eq(64)))
                .thenAnswer(invocation -> List.of(invocation.getArgument(0, String.class)));

        List<Chunker.PageChunk> children = DocumentProcessorImpl.splitChildrenByPage(List.of(
                new Chunker.PageChunk("first", null),
                new Chunker.PageChunk("second", null)), chunker);

        assertEquals(1, children.size());
        assertEquals(null, children.get(0).pageNo());
        assertEquals("first\n\nsecond", children.get(0).content());
    }
}
