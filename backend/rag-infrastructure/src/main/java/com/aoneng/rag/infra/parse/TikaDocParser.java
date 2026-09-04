package com.aoneng.rag.infra.parse;

import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.ocr.TesseractOCRConfig;
import org.apache.tika.parser.pdf.PDFParserConfig;
import org.apache.tika.sax.BodyContentHandler;
import org.apache.pdfbox.io.MemoryUsageSetting;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 文档解析服务实现（基于 Apache Tika）。
 */
@Component
public class TikaDocParser implements DocParser {

    private static final Map<String, Set<String>> EXPECTED_MEDIA_TYPES = Map.ofEntries(
            Map.entry("pdf", Set.of("application/pdf")),
            Map.entry("doc", Set.of("application/msword")),
            Map.entry("docx", Set.of("application/vnd.openxmlformats-officedocument.wordprocessingml.document", "application/x-tika-ooxml")),
            Map.entry("xls", Set.of("application/vnd.ms-excel")),
            Map.entry("xlsx", Set.of("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "application/x-tika-ooxml")),
            Map.entry("ppt", Set.of("application/vnd.ms-powerpoint")),
            Map.entry("pptx", Set.of("application/vnd.openxmlformats-officedocument.presentationml.presentation", "application/x-tika-ooxml")),
            Map.entry("rtf", Set.of("application/rtf", "text/rtf")),
            Map.entry("wp", Set.of("application/vnd.wordperfect", "application/x-wordperfect")),
            Map.entry("jpg", Set.of("image/jpeg")),
            Map.entry("jpeg", Set.of("image/jpeg")),
            Map.entry("png", Set.of("image/png")),
            Map.entry("md", Set.of("text/markdown", "text/plain", "application/octet-stream")),
            Map.entry("txt", Set.of("text/plain", "application/octet-stream"))
    );

    private final Tika tika = new Tika();

    /** Detects actual MIME using lightweight type detection only. */
    public String detectMime(InputStream input, String fileName) throws IOException {
        return tika.detect(input, fileName);
    }

    @Override
    public void validateUpload(InputStream input, String fileName, String extension) throws IOException {
        String normalizedExtension = extension == null ? "" : extension.toLowerCase(java.util.Locale.ROOT);
        String detected = detectMime(input, fileName);
        if ("pdf".equals(normalizedExtension) && !"application/pdf".equals(detected)) {
            throw new IllegalArgumentException("文件扩展名为 PDF，但文件内容不是有效的 PDF 文档");
        }
        Set<String> expected = EXPECTED_MEDIA_TYPES.get(normalizedExtension);
        if (expected != null && !expected.contains(detected)) {
            throw new IllegalArgumentException("文件内容与扩展名不匹配，检测到的类型为 " + detected);
        }
    }

    @Override
    public String parse(InputStream input, String fileName, String extension)
            throws IOException, TikaException, SAXException {
        AutoDetectParser parser = new AutoDetectParser();
        BodyContentHandler handler = new BodyContentHandler(-1);
        Metadata metadata = new Metadata();
        metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, fileName);
        ParseContext context = new ParseContext();

        if ("pdf".equals(extension)) {
            PDFParserConfig pdfConfig = new PDFParserConfig();
            pdfConfig.setOcrStrategy(PDFParserConfig.OCR_STRATEGY.AUTO);
            context.set(PDFParserConfig.class, pdfConfig);

            TesseractOCRConfig ocrConfig = new TesseractOCRConfig();
            ocrConfig.setLanguage("chi_sim+eng");
            ocrConfig.setTimeoutSeconds(120);
            context.set(TesseractOCRConfig.class, ocrConfig);
        }

        parser.parse(input, handler, metadata, context);
        return cleanText(handler.toString());
    }

    @Override
    public List<ParsedPage> parsePdfPages(InputStream input) throws IOException {
        List<ParsedPage> pages = new ArrayList<>();
        try (PDDocument document = PDDocument.load(input, MemoryUsageSetting.setupTempFileOnly())) {
            PDFTextStripper stripper = new PDFTextStripper();
            for (int pageNo = 1; pageNo <= document.getNumberOfPages(); pageNo++) {
                stripper.setStartPage(pageNo);
                stripper.setEndPage(pageNo);
                String content = cleanText(stripper.getText(document));
                if (!content.isBlank()) pages.add(new ParsedPage(pageNo, content));
            }
        }
        return pages;
    }

    private String cleanText(String source) {
        String text = source == null ? "" : source
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .replace('\u0000', ' ')
                .replaceAll("[\\p{C}&&[^\\n\\t]]", " ")
                .replaceAll("[\\t ]+", " ")
                .replaceAll(" *\\n *", "\n")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
        return text.codePoints().anyMatch(Character::isLetterOrDigit) ? text : "";
    }
}
