package com.aoneng.rag.infra.parse;

import com.aoneng.rag.infra.config.DocumentParserProperties;
import org.apache.tika.exception.TikaException;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Routes native documents and scanned PDFs to the appropriate parser. */
@Primary
@Component
public class DocumentParseRouter implements StructuredDocumentParser {
    private static final java.util.Set<String> DOCLING_EXTENSIONS = java.util.Set.of("pdf", "docx", "pptx", "xlsx");
    private static final java.util.Set<String> LEGACY_TIKA_EXTENSIONS = java.util.Set.of("doc", "xls", "ppt", "rtf", "wp", "md", "txt");
    private static final java.util.Set<String> IMAGE_EXTENSIONS = java.util.Set.of("jpg", "jpeg", "png");

    private final TikaDocParser tika;
    private final DoclingServeClient docling;
    private final PaddleOcrClient paddleOcr;
    private final PdfScanClassifier scanClassifier;
    private final DocumentParserProperties properties;

    public DocumentParseRouter(TikaDocParser tika, DoclingServeClient docling,
                               PaddleOcrClient paddleOcr, PdfScanClassifier scanClassifier,
                               DocumentParserProperties properties) {
        this.tika = tika;
        this.docling = docling;
        this.paddleOcr = paddleOcr;
        this.scanClassifier = scanClassifier;
        this.properties = properties;
    }

    @Override
    public void validateUpload(InputStream input, String fileName, String extension) throws IOException {
        tika.validateUpload(input, fileName, extension);
    }

    @Override
    public String parse(InputStream input, String fileName, String extension)
            throws IOException, TikaException, SAXException {
        return parseStructured(input, fileName, extension).text();
    }

    @Override
    public StructuredDocument parseStructured(InputStream input, String fileName, String extension)
            throws IOException {
        String normalizedExtension = extension == null ? "" : extension.toLowerCase(java.util.Locale.ROOT);
        Path source = Files.createTempFile("document-route-", "." + (normalizedExtension.isBlank() ? "bin" : normalizedExtension));
        try {
            copyBounded(input, source);
            String mime = detectMime(source, fileName);
            ensureMimeMatchesRoute(normalizedExtension, mime);
            if ("pdf".equals(normalizedExtension)) {
                PdfScanClassifier.Classification classification = scanClassifier.classify(source);
                if (classification.scanned()) return parseOcr(source, fileName, normalizedExtension, classification);
                return parseDocling(source, fileName, normalizedExtension, classification);
            }
            if (IMAGE_EXTENSIONS.contains(normalizedExtension)) return parseOcr(source, fileName, normalizedExtension, null);
            if (DOCLING_EXTENSIONS.contains(normalizedExtension)) return parseDocling(source, fileName, normalizedExtension, null);
            if (LEGACY_TIKA_EXTENSIONS.contains(normalizedExtension)) return parseTika(source, fileName, normalizedExtension, null, "tika");
            throw new IOException("Unsupported document type for parser routing: " + normalizedExtension);
        } finally {
            Files.deleteIfExists(source);
        }
    }

    @Override
    public List<ParsedPage> parsePdfPages(InputStream input) throws IOException {
        StructuredDocument parsed = parseStructured(input, "document.pdf", "pdf");
        Map<Integer, StringBuilder> grouped = new HashMap<>();
        for (Block block : parsed.blocks()) {
            if (block.pageNo() != null && !block.text().isBlank()) {
                grouped.computeIfAbsent(block.pageNo(), ignored -> new StringBuilder())
                        .append(block.text()).append('\n');
            }
        }
        return grouped.entrySet().stream()
                .map(entry -> new ParsedPage(entry.getKey(), entry.getValue().toString().trim()))
                .sorted(Comparator.comparingInt(ParsedPage::pageNo)).toList();
    }

    private StructuredDocument parseDocling(Path source, String fileName, String extension,
                                            PdfScanClassifier.Classification classification) throws IOException {
        try (InputStream input = Files.newInputStream(source)) {
            StructuredDocument parsed = docling.parseStructured(input, fileName, extension);
            if (hasContent(parsed)) return withMetadata(parsed, "docling", false, classification, null);
            throw new IOException("Docling returned empty content");
        } catch (Exception failure) {
            return parseTika(source, fileName, extension, classification, "tika-fallback", failure);
        }
    }

    private StructuredDocument parseOcr(Path source, String fileName, String extension,
                                        PdfScanClassifier.Classification classification) throws IOException {
        Exception ocrFailure;
        try (InputStream input = Files.newInputStream(source)) {
            StructuredDocument parsed = paddleOcr.parseStructured(input, fileName);
            if (hasContent(parsed)) return withMetadata(parsed, "paddleocr", true, classification, null);
            ocrFailure = new IOException("PaddleOCR returned empty content");
        } catch (Exception failure) {
            ocrFailure = failure;
        }

        boolean tikaProducedText = false;
        try (InputStream fallback = Files.newInputStream(source)) {
            String text = tika.parse(fallback, fileName, extension);
            tikaProducedText = text != null && !text.isBlank();
        } catch (Exception ignored) {
            // The OCR route remains authoritative for scanned documents.
        }
        throw new ScannedPdfParseException("PaddleOCR failed", tikaProducedText, ocrFailure);
    }

    private StructuredDocument parseTika(Path source, String fileName, String extension,
                                         PdfScanClassifier.Classification classification, String parserName)
            throws IOException {
        return parseTika(source, fileName, extension, classification, parserName, null);
    }

    private StructuredDocument parseTika(Path source, String fileName, String extension,
                                         PdfScanClassifier.Classification classification, String parserName,
                                         Exception failure) throws IOException {
        try (InputStream fallback = Files.newInputStream(source)) {
            String text = tika.parse(fallback, fileName, extension);
            if (text == null || text.isBlank()) throw new IOException("Tika returned empty content", failure);
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("parser", parserName);
            metadata.put("parse_method", parserName);
            if (failure != null) metadata.put("fallback_reason", reason(failure));
            if (classification != null) {
                metadata.put("isScanned", false);
                metadata.put("pdf_text_page_ratio", classification.textPageRatio());
            }
            return new StructuredDocument(text,
                    List.of(new Block("paragraph", text, null, 0, 0, Map.of(), metadata)), metadata);
        } catch (Exception tikaFailure) {
            throw new IOException("Tika parsing failed", tikaFailure);
        }
    }

    private String detectMime(Path source, String fileName) throws IOException {
        try (InputStream input = Files.newInputStream(source)) {
            return tika.detectMime(input, fileName);
        }
    }

    private void ensureMimeMatchesRoute(String extension, String mime) throws IOException {
        if ("pdf".equals(extension) && !"application/pdf".equals(mime)) {
            throw new IOException("File content is not a valid PDF");
        }
        if (IMAGE_EXTENSIONS.contains(extension) && !mime.startsWith("image/")) {
            throw new IOException("File content is not a valid image");
        }
    }

    private boolean hasContent(StructuredDocument document) {
        if (document == null) return false;
        if (document.text() != null && !document.text().isBlank()) return true;
        return document.blocks().stream().anyMatch(block -> block.text() != null && !block.text().isBlank());
    }

    private StructuredDocument withMetadata(StructuredDocument source, String parserName, boolean scanned,
                                            PdfScanClassifier.Classification classification, String fallbackReason) {
        Map<String, Object> documentMetadata = new HashMap<>(source.metadata());
        documentMetadata.put("parser", parserName);
        documentMetadata.put("parse_method", parserName);
        documentMetadata.put("isScanned", scanned);
        if (fallbackReason != null) documentMetadata.put("fallback_reason", fallbackReason);
        if (classification != null) {
            documentMetadata.put("pdf_page_count", classification.pageCount());
            documentMetadata.put("pdf_text_pages", classification.textPages());
            documentMetadata.put("pdf_text_page_ratio", classification.textPageRatio());
        }
        List<Block> blocks = new ArrayList<>();
        for (Block block : source.blocks()) {
            Map<String, Object> metadata = new HashMap<>(block.metadata());
            metadata.put("parser", parserName);
            metadata.put("parse_method", parserName);
            metadata.put("isScanned", scanned);
            blocks.add(new Block(block.type(), block.text(), block.pageNo(), block.level(), block.order(),
                    block.bbox(), metadata));
        }
        return new StructuredDocument(source.text(), blocks, documentMetadata);
    }

    private String reason(Exception failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank() ? failure.getClass().getSimpleName() :
                message.substring(0, Math.min(200, message.length()));
    }

    private void copyBounded(InputStream input, Path target) throws IOException {
        try (var output = Files.newOutputStream(target)) {
            byte[] buffer = new byte[8192];
            long total = 0;
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > properties.maxBytes()) throw new IOException("Document exceeds parser size limit");
                output.write(buffer, 0, read);
            }
        }
    }
}
