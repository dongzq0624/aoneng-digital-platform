package com.aoneng.rag.infra.parse;

import java.io.IOException;

/** Indicates that a scanned PDF could not be indexed by the OCR route. */
public class ScannedPdfParseException extends IOException {
    private final boolean tikaProducedText;

    public ScannedPdfParseException(String message, boolean tikaProducedText, Throwable cause) {
        super(message, cause);
        this.tikaProducedText = tikaProducedText;
    }

    public boolean tikaProducedText() {
        return tikaProducedText;
    }
}
