package com.aoneng.rag.infra.chunk;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TextChunkerTest {

    @Test
    void tokenCountIsDelegatedToModelTokenizer() {
        ModelTokenizer tokenizer = mock(ModelTokenizer.class);
        when(tokenizer.count(anyString())).thenReturn(7);

        TextChunker chunker = new TextChunker(tokenizer);

        assertEquals(7, chunker.countTokens("任意文本"));
    }

    @Test
    void tokenBudgetControlsChunking() {
        ModelTokenizer tokenizer = mock(ModelTokenizer.class);
        when(tokenizer.count(anyString())).thenAnswer(invocation ->
                ((String) invocation.getArgument(0)).codePointCount(0,
                        ((String) invocation.getArgument(0)).length()));

        TextChunker chunker = new TextChunker(tokenizer);
        var chunks = chunker.splitTokens("一二三四五六七八九十", 4, 0);

        assertFalse(chunks.isEmpty());
        chunks.forEach(chunk -> org.junit.jupiter.api.Assertions.assertTrue(chunker.countTokens(chunk) <= 4));
    }

    @Test
    void longInputIsNotCollapsedIntoTokenizerMaximum() {
        ModelTokenizer tokenizer = mock(ModelTokenizer.class);
        when(tokenizer.count(anyString())).thenAnswer(invocation ->
                ((String) invocation.getArgument(0)).codePointCount(0,
                        ((String) invocation.getArgument(0)).length()));

        TextChunker chunker = new TextChunker(tokenizer);
        String text = "字".repeat(2_600);
        var chunks = chunker.splitTokens(text, 1_200, 64);

        org.junit.jupiter.api.Assertions.assertTrue(chunks.size() > 1);
        chunks.forEach(chunk -> org.junit.jupiter.api.Assertions.assertTrue(chunker.countTokens(chunk) <= 1_200));
    }
}
