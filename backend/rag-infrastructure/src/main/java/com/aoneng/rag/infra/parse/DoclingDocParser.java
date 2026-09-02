package com.aoneng.rag.infra.parse;

import com.aoneng.rag.infra.config.DoclingSamplingProperties;

/** Backward-compatible facade for the Docling-Serve client. */
@Deprecated
public class DoclingDocParser extends DoclingServeClient {
    public DoclingDocParser(TikaDocParser tika, DoclingSamplingProperties properties) {
        super(tika, properties);
    }
}
