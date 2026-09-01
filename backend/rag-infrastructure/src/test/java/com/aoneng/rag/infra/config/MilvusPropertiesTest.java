package com.aoneng.rag.infra.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MilvusPropertiesTest {

    @Test
    void appliesDevelopmentDefaultsAndClampsThreshold() {
        MilvusProperties properties = new MilvusProperties(null, 0, null, null, 0, 2.0);

        assertEquals("localhost", properties.host());
        assertEquals(19530, properties.port());
        assertEquals("kb_embeddings", properties.collection());
        assertEquals(1024, properties.dimension());
        assertEquals(1.0, properties.scoreThreshold());
    }
}
