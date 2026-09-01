package com.example.rag.service.impl;

import com.example.rag.service.DocParseService;
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
import org.springframework.stereotype.Service;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 文档解析服务默认实现。
 * 使用 Apache Tika 进行通用文本提取，PDFBox 进行逐页枚举，
 * Tesseract OCR 处理扫描版 PDF。
 */
@Service
public class DocParseServiceImpl implements DocParseService {

    /** 文件扩展名到合法 MIME 类型的映射。 */
    private static final Map<String, Set<String>> EXPECTED_MEDIA_TYPES = Map.of(
            "pdf", Set.of("application/pdf"),
            "doc", Set.of("application/msword"),
            "docx", Set.of("application/vnd.openxmlformats-officedocument.wordprocessingml.document", "application/x-tika-ooxml"),
            "xls", Set.of("application/vnd.ms-excel"),
            "xlsx", Set.of("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "application/x-tika-ooxml"),
            "ppt", Set.of("application/vnd.ms-powerpoint"),
            "pptx", Set.of("application/vnd.openxmlformats-officedocument.presentationml.presentation", "application/x-tika-ooxml")
    );

    private final Tika tika = new Tika();

    /**
     * 验证上传文件。嗅探文件 MIME 类型并与扩展名比对，
     * 防止用户上传伪装的危险文件。
     *
     * @param input     文件输入流
     * @param fileName  文件名
     * @param extension 文件扩展名
     * @throws IOException 读取错误或 MIME 类型不匹配时抛出
     */
    @Override
    public void validateUpload(InputStream input, String fileName, String extension) throws IOException {
        String detected = tika.detect(input, fileName);
        if ("pdf".equals(extension) && !"application/pdf".equals(detected)) {
            throw new IllegalArgumentException("文件扩展名为 PDF，但文件内容不是有效的 PDF 文档");
        }
        Set<String> expected = EXPECTED_MEDIA_TYPES.get(extension);
        if (expected != null && !expected.contains(detected)) {
            throw new IllegalArgumentException("文件内容与扩展名不匹配，检测到的类型为 " + detected);
        }
    }

    /**
     * 使用 Apache Tika 提取文本内容。
     * 对 PDF 自动启用 OCR 策略，中文识别语言为 chi_sim+eng。
     *
     * @param input     文件输入流
     * @param fileName  文件名
     * @param extension 文件扩展名
     * @return 提取并清理后的文本内容
     */
    @Override
    public String parse(InputStream input, String fileName, String extension) throws IOException, TikaException, SAXException {
        AutoDetectParser parser = new AutoDetectParser();
        BodyContentHandler handler = new BodyContentHandler(-1);
        Metadata metadata = new Metadata();
        metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, fileName);
        ParseContext context = new ParseContext();

        if ("pdf".equals(extension)) {
            // 对 PDF 配置 OCR 参数：自动模式 + 中文 + 英文
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

    /**
     * 独立提取每个 PDF 页面，以便索引的分块保留可靠的来源页码。
     *
     * @param input PDF 文件输入流
     * @return 解析后的页面列表
     * @throws IOException PDF 读取错误时抛出
     */
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

    /**
     * 清理解析出的文本：统一换行符、过滤控制字符、规范化空白。
     * 当文本不含字母或数字时返回空字符串。
     */
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
