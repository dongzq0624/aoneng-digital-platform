package com.aoneng.rag.infra.llm;

import reactor.core.publisher.Flux;

import java.util.List;
import java.util.function.Consumer;

/**
 * 大语言模型服务接口。
 * 统一封装嵌入、查询改写、重排、阻塞聊天和流式聊天能力。
 */
public interface LlmService {

    /**
     * 重排结果记录。
     *
     * @param index 重排后的文档索引
     * @param score 相关性评分
     */
    record RerankResult(int index, double score) {
    }

    /**
     * 将文本块嵌入为向量。
     *
     * @param text 待嵌入的文本
     * @return 嵌入向量（通常 1024 维）
     * @throws IllegalArgumentException 输入为空时抛出
     */
    List<Float> embed(String text);

    /**
     * 生成替代搜索查询改写。
     *
     * @param question 原始问题
     * @param maximum 最大改写数量
     * @return 改写后的查询列表
     */
    List<String> rewriteQueries(String question, int maximum);

    /**
     * 对文档列表进行重排。
     *
     * @param query     查询问题
     * @param documents 待重排的文档列表
     * @param maximum  返回的最大结果数
     * @return 重排结果列表
     */
    List<RerankResult> rerank(String query, List<String> documents, int maximum);

    /**
     * 阻塞式聊天补全。
     *
     * @param question 用户问题
     * @param context 上下文（包含引用文档）
     * @return 模型回答
     */
    String chat(String question, String context);

    /**
     * 通过回调函数流式返回模型回答。
     *
     * @param question  用户问题
     * @param context   上下文
     * @param onDelta   每个非空增量文本的回调
     * @param cancelled 取消检查
     */
    void streamChat(String question, String context, Consumer<String> onDelta,
                    java.util.function.BooleanSupplier cancelled);

    /**
     * 响应式流式聊天。
     *
     * @param question 用户问题
     * @param context  上下文
     * @return 文本增量流
     */
    Flux<String> streamChatFlux(String question, String context);
}
