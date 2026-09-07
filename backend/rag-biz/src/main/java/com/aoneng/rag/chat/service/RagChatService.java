package com.aoneng.rag.chat.service;

import com.aoneng.rag.application.repository.KbScope;
import com.aoneng.rag.application.repository.PlatformRepository;
import com.aoneng.rag.application.retrieval.RetrievalService;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

/**
 * RAG 对话流水线服务接口。负责用户权限解析、引用分组和响应式 RAG 流水线。
 * 控制器将其包装为 Spring SSE 响应返回。
 */
public interface RagChatService {

    KbScope requireChatAccess(String username);

    KbScope requireAdmin(String username);

    List<Long> permittedKbIds(KbScope scope, List<Long> requestedKbIds);

    PlatformRepository.ChatTurn prepareTurn(long userId, Long conversationId, String question,
                                          List<Long> permittedKbIds);

    RetrievalService.RetrievalResult retrieve(String question, List<Long> permittedKbIds);

    Flux<ServerSentEvent<Object>> streamAnswer(KbScope scope,
                                               PlatformRepository.ChatTurn turn,
                                               String username,
                                               String question,
                                               List<Long> permittedKbIds);

    ChatStreamPayload buildStreamPayload(RetrievalService.RetrievalResult result,
                                        String question,
                                        KbScope scope,
                                        PlatformRepository.ChatTurn turn,
                                        List<Long> permittedKbIds);

    Map<String, Object> completeTurn(PlatformRepository.ChatTurn turn,
                                     KbScope scope,
                                     String question,
                                     String answerText,
                                     List<Long> permittedKbIds,
                                     List<Long> chunkIds,
                                     Map<String, Map<String, Object>> citationsBySource,
                                     List<Map<String, Object>> citations,
                                     Map<String, Object> trace);

    void failTurn(PlatformRepository.ChatTurn turn, String message);

    String removeSourceMarkers(String answer);

    record ChatStreamPayload(String modelContext,
                            List<Map<String, Object>> sources,
                            Map<String, Map<String, Object>> citationsBySource,
                            List<Long> chunkIds) {
    }
}
