package com.aoneng.rag.infra.chunk;

import java.util.List;

/**
 * 文本分块服务接口。
 * 将长文本切分为固定大小的重叠块，支持按页切分。
 */
public interface Chunker {

    /**
     * 将纯文本切分为固定大小的重叠块。
     *
     * @param text     待切分的文本
     * @param maxChars 每块最大字符数（最小 128）
     * @param overlap  相邻块之间的重叠字符数
     * @return 分块文本列表
     */
    List<String> split(String text, int maxChars, int overlap);

    /**
     * 将带页码的文本切分为重叠的分块。
     *
     * @param text     待切分的文本
     * @param pageNo   页码
     * @param maxChars 每块最大字符数
     * @param overlap  重叠字符数
     * @return 带页码的分块列表
     */
    List<PageChunk> splitPage(String text, int pageNo, int maxChars, int overlap);

    /**
     * 带页码的分块记录。
     *
     * @param content 分块文本内容
     * @param pageNo  页码（可为 null）
     */
    record PageChunk(String content, Integer pageNo) {
    }
}
