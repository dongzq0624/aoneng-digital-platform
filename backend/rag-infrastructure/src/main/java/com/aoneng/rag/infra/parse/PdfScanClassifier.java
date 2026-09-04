package com.aoneng.rag.infra.parse;

import com.aoneng.rag.infra.config.DocumentParserProperties;
import org.apache.pdfbox.io.MemoryUsageSetting;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;

/** Classifies PDFs by the amount of usable text in their embedded text layer. */
@Component
public class PdfScanClassifier {
    private final DocumentParserProperties properties;

    public PdfScanClassifier(DocumentParserProperties properties) {
        this.properties = properties;
    }

    public Classification classify(Path source) throws IOException {
        try (InputStream input = java.nio.file.Files.newInputStream(source);
             PDDocument document = PDDocument.load(input, MemoryUsageSetting.setupTempFileOnly())) {
            int pageCount = document.getNumberOfPages();
            if (pageCount == 0) return new Classification(true, 0, 0, 0, 0D);

            PDFTextStripper stripper = new PDFTextStripper();
            int textPages = 0;
            long textChars = 0;
            for (int pageNo = 1; pageNo <= pageCount; pageNo++) {
                stripper.setStartPage(pageNo);
                stripper.setEndPage(pageNo);
                String text = stripper.getText(document);
                int chars = usableChars(text);
                textChars += chars;
                if (chars >= properties.pdfMinTextChars()) textPages++;
            }
            double ratio = (double) textPages / pageCount;
            return new Classification(ratio < properties.pdfNativePageRatio(), pageCount,
                    textPages, textChars, ratio);
        }
    }

    private int usableChars(String text) {
        if (text == null || text.isBlank()) return 0;
        return (int) text.codePoints().filter(Character::isLetterOrDigit).count();
    }

    public record Classification(boolean scanned, int pageCount, int textPages,
                                 long textChars, double textPageRatio) {
    }
}
