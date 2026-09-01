package com.aoneng.rag.application.chat;

import com.aoneng.rag.application.repository.KbScope;
import com.aoneng.rag.application.repository.PlatformRepository;
import com.aoneng.rag.application.retrieval.RetrievalService;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

/**
 * RAG 对话应用服务接口。
 * 编排用户权限解析、引用分组和响应式 RAG 流水线。
 */
public interface ChatService {

    KbScope requireChatAccess(String username);

    KbScope requireAdmin(String username);

    List<Long> permittedKbIds(KbScope scope, List<Long> requestedKbIds);

    PlatformRepository.ChatTurn prepareTurn(long userId, Long conversationId, String question,
                                           List<Long> permittedKbIds);

    RetrievalService.RetrievalResult retrieve(String question, List<Long> permittedKbIds);

    Flux<ServerSentEvent<Object>> streamAnswer(KbScope scope,
                                               PlatformRepository.ChatTurn turn,
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
                                     Map<String, Object> trace);

    void failTurn(PlatformRepository.ChatTurn turn, String message);

    String removeSourceMarkers(String answer);

    record ChatStreamPayload(String modelContext,
                             List<Map<String, Object>> sources,
                             Map<String, Map<String, Object>> citationsBySource,
                             List<Long> chunkIds) {
    }
}
