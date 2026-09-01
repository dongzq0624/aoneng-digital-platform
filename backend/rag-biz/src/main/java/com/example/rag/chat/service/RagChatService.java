package com.example.rag.chat.service;

import com.example.rag.service.PlatformRepository;
import com.example.rag.service.RagRetrievalService;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

/**
 * RAG 对话流水线服务接口。负责用户权限解析、引用分组和响应式 RAG 流水线。
 * 控制器将其包装为 Spring SSE 响应返回。
 */
public interface RagChatService {

    /**
     * 校验用户是否有知识库聊天权限。
     *
     * @param username 用户名
     * @return 用户可访问的知识库范围
     * @throws org.springframework.web.server.ResponseStatusException 无权限时返回 403
     */
    PlatformRepository.KbScope requireChatAccess(String username);

    /**
     * 校验用户是否为管理员。
     *
     * @param username 用户名
     * @return 用户可访问的知识库范围
     * @throws org.springframework.web.server.ResponseStatusException 非管理员时返回 403
     */
    PlatformRepository.KbScope requireAdmin(String username);

    /**
     * 计算用户在指定知识库列表中的有效权限。
     *
     * @param scope          用户权限范围
     * @param requestedKbIds 前端请求的知识库 ID 列表（可为空或空列表表示全部）
     * @return 过滤后的有效知识库 ID 列表
     */
    List<Long> permittedKbIds(PlatformRepository.KbScope scope, List<Long> requestedKbIds);

    /**
     * 准备对话轮次。创建新会话或关联到已有会话。
     *
     * @param userId        用户 ID
     * @param conversationId 会话 ID（可为空，表示新会话）
     * @param question      用户问题
     * @param permittedKbIds 用户可访问的知识库 ID 列表
     * @return 对话轮次信息
     */
    PlatformRepository.ChatTurn prepareTurn(long userId, Long conversationId, String question,
                                          List<Long> permittedKbIds);

    /**
     * 执行向量检索。
     *
     * @param question      用户问题
     * @param permittedKbIds 用户可访问的知识库 ID 列表
     * @return 检索结果
     */
    RagRetrievalService.RetrievalResult retrieve(String question, List<Long> permittedKbIds);

    /**
     * 完整的响应式 RAG 流水线，返回可直接返回的 SSE 事件流。
     *
     * @param scope          用户权限范围
     * @param turn           对话轮次
     * @param question       用户问题
     * @param permittedKbIds 用户可访问的知识库 ID 列表
     * @return SSE 事件流
     */
    Flux<ServerSentEvent<Object>> streamAnswer(PlatformRepository.KbScope scope,
                                               PlatformRepository.ChatTurn turn,
                                               String question,
                                               List<Long> permittedKbIds);

    /**
     * 构建流式载荷。包含模型上下文、公开引用列表和被引用的分块 ID。
     *
     * @param result          检索结果
     * @param question        用户问题
     * @param scope           用户权限范围
     * @param turn            对话轮次
     * @param permittedKbIds  用户可访问的知识库 ID 列表
     * @return 流式载荷
     */
    ChatStreamPayload buildStreamPayload(RagRetrievalService.RetrievalResult result,
                                        String question,
                                        PlatformRepository.KbScope scope,
                                        PlatformRepository.ChatTurn turn,
                                        List<Long> permittedKbIds);

    /**
     * 完成对话轮次。持久化问答记录。
     *
     * @param turn           对话轮次
     * @param scope          用户权限范围
     * @param question       用户问题
     * @param answerText     模型回答
     * @param permittedKbIds 用户可访问的知识库 ID 列表
     * @param chunkIds       引用的分块 ID 列表
     * @param citationsBySource 按来源分组的引用信息
     * @param trace          追踪信息
     * @return 完成后的轮次信息
     */
    Map<String, Object> completeTurn(PlatformRepository.ChatTurn turn,
                                     PlatformRepository.KbScope scope,
                                     String question,
                                     String answerText,
                                     List<Long> permittedKbIds,
                                     List<Long> chunkIds,
                                     Map<String, Map<String, Object>> citationsBySource,
                                     Map<String, Object> trace);

    /**
     * 标记对话轮次失败。
     *
     * @param turn   对话轮次
     * @param message 错误信息
     */
    void failTurn(PlatformRepository.ChatTurn turn, String message);

    /**
     * 移除回答中的来源标记（如 [来源1] 等）。
     *
     * @param answer 原始回答
     * @return 清理后的回答
     */
    String removeSourceMarkers(String answer);

    /**
     * 检索完成后交给控制器的聚合载荷。
     * 包含模型上下文、公开来源列表和被引用的分块 ID。
     *
     * @param modelContext       模型上下文（带引用标记）
     * @param sources            公开来源列表
     * @param citationsBySource  按来源分组的引用
     * @param chunkIds           被引用的分块 ID 列表
     */
    record ChatStreamPayload(String modelContext,
                            List<Map<String, Object>> sources,
                            Map<String, Map<String, Object>> citationsBySource,
                            List<Long> chunkIds) {
    }
}
