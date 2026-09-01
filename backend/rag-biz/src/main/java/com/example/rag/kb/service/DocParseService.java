package com.example.rag.kb.service;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Set;

/**
 * 文档解析服务接口。封装 Apache Tika + PDFBox，负责验证上传文件、
 * 解析文本和枚举 PDF 页面。PDF 保留 OCR 策略为 {@code AUTO}，
 * 中文 OCR 语言为 {@code chi_sim+eng}。
 */
public interface DocParseService {

    /**
     * 支持的文件扩展名。与 {@link com.example.rag.common.constant.FileConstants#ALLOWED_EXTENSIONS} 保持同步。
     */
    Set<String> EXPECTED_MEDIA_TYPES = com.example.rag.common.constant.FileConstants.ALLOWED_EXTENSIONS;

    /**
     * 解析后的页面记录。
     *
     * @param pageNo  页码（从 1 开始）
     * @param content 页面文本内容
     */
    record ParsedPage(int pageNo, String content) { }

    /**
     * 验证上传文件。当嗅探的 MIME 类型与文件扩展名不一致时拒绝。
     *
     * @param input     文件输入流
     * @param fileName  文件名
     * @param extension 文件扩展名
     * @throws IOException 输入输出错误时抛出
     */
    void validateUpload(InputStream input, String fileName, String extension) throws IOException;

    /**
     * 使用 Apache Tika（对扫描 PDF 使用 Tesseract OCR）提取文本。
     *
     * @param input     文件输入流
     * @param fileName  文件名
     * @param extension 文件扩展名
     * @return 提取的文本内容
     * @throws IOException       输入输出错误时抛出
     * @throws org.apache.tika.exception.TikaException Tika 解析错误时抛出
     * @throws org.xml.sax.SAXException              XML 解析错误时抛出
     */
    String parse(InputStream input, String fileName, String extension) throws IOException, org.apache.tika.exception.TikaException, org.xml.sax.SAXException;

    /**
     * 独立提取每个 PDF 页面，以便索引的分块保留可靠的来源页码。
     *
     * @param input PDF 文件输入流
     * @return 解析后的页面列表
     * @throws IOException 输入输出错误时抛出
     */
    List<ParsedPage> parsePdfPages(InputStream input) throws IOException;
}
