package com.example.rag.service;

import reactor.core.publisher.Flux;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * 通义千问（OpenAI 兼容）集成服务接口。涵盖嵌入、查询改写、重排、
 * 阻塞聊天和流式聊天（回调式和响应式两种变体）。
 * 实现类负责保护 API Key 并提供用户友好的错误提示。
 */
public interface DashScopeService {

    /**
     * 重排结果记录。
     *
     * @param index 重排后的文档索引
     * @param score 相关性评分
     */
    record RerankResult(int index, double score) {
    }

    /**
     * 将文本块嵌入为 1024 维向量。
     *
     * @param text 待嵌入的文本
     * @return 嵌入向量
     * @throws IllegalArgumentException 输入为空时抛出
     * @throws IllegalStateException    DASHSCOPE_API_KEY 未配置时抛出
     */
    List<Float> embed(String text);

    /**
     * 生成最多 {@code maximum} 个替代搜索查询改写。尽力而为；模型调用失败时返回空列表。
     *
     * @param question 原始问题
     * @param maximum 最大改写数量
     * @return 改写后的查询列表
     */
    List<String> rewriteQueries(String question, int maximum);

    /**
     * 使用通义千问重排 API 对文档进行重排。
     *
     * @param query    查询问题
     * @param documents 待重排的文档列表
     * @param maximum 返回的最大结果数
     * @return 重排结果列表
     */
    List<RerankResult> rerank(String query, List<String> documents, int maximum);

    /**
     * 阻塞式聊天补全。返回模型作为单个字符串的回答。
     *
     * @param question 用户问题
     * @param context  上下文（包含引用文档）
     * @return 模型回答
     */
    String chat(String question, String context);

    /**
     * 通过回调函数流式返回模型回答。
     *
     * @param question  用户问题
     * @param context   上下文
     * @param onDelta   每个非空增量文本的回调
     * @param cancelled 用于停止底层读取的取消检查
     */
    void streamChat(String question, String context, Consumer<String> onDelta,
                    BooleanSupplier cancelled);

    /**
     * 响应式流式变体，供 WebFlux 聊天流水线使用。
     *
     * @param question 用户问题
     * @param context  上下文
     * @return 文本增量流
     */
    Flux<String> streamChatFlux(String question, String context);
}
