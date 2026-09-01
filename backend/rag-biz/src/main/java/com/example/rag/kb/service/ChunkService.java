package com.example.rag.kb.service;

import java.util.List;

/**
 * 文本分块服务接口。将原始文档文本切分为固定大小的重叠块，
 * 并在可用时保留原始页码信息。
 */
public interface ChunkService {

    /**
     * 分块记录，包含文本内容和页码。
     *
     * @param content 分块文本内容
     * @param pageNo  原始页码（可为空）
     */
    record PageChunk(String content, Integer pageNo) { }

    /**
     * 将纯文本切分为重叠的分块。
     *
     * @param text     待切分的文本
     * @param maxChars 每块最大字符数
     * @param overlap  相邻块之间的重叠字符数
     * @return 分块文本列表
     */
    List<String> split(String text, int maxChars, int overlap);

    /**
     * 将带页码的文本切分为重叠的分块。
     *
     * @param text     待切分的文本
     * @param pageNo   原始页码
     * @param maxChars 每块最大字符数
     * @param overlap  相邻块之间的重叠字符数
     * @return 分块记录列表
     */
    List<PageChunk> splitPage(String text, int pageNo, int maxChars, int overlap);
}
