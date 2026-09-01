package com.aoneng.rag.infra.parse;

import org.apache.tika.exception.TikaException;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * 文档解析服务接口。
 * 统一封装文件类型校验、文本提取和 PDF 页面解析能力。
 */
public interface DocParser {

    /**
     * 验证上传文件。
     *
     * @param input     文件输入流
     * @param fileName  文件名
     * @param extension 文件扩展名
     * @throws IOException 文件读取错误或 MIME 类型不匹配时抛出
     */
    void validateUpload(InputStream input, String fileName, String extension) throws IOException;

    /**
     * 提取文本内容。
     *
     * @param input     文件输入流
     * @param fileName  文件名
     * @param extension 文件扩展名
     * @return 提取并清理后的文本内容
     */
    String parse(InputStream input, String fileName, String extension)
            throws IOException, TikaException, SAXException;

    /**
     * 解析 PDF 页面。
     *
     * @param input PDF 文件输入流
     * @return 解析后的页面列表
     * @throws IOException PDF 读取错误时抛出
     */
    List<ParsedPage> parsePdfPages(InputStream input) throws IOException;

    /**
     * PDF 解析后的单页记录。
     *
     * @param pageNo 页码（从 1 开始）
     * @param content 该页提取的文本内容
     */
    record ParsedPage(int pageNo, String content) {
    }
}
