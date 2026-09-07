package com.aoneng.rag.chat.controller;

import com.aoneng.rag.chat.dto.ChatDTO;
import com.aoneng.rag.chat.dto.ConversationPageQuery;
import com.aoneng.rag.chat.dto.CreateConversationDTO;
import com.aoneng.rag.chat.dto.CreateEvalCaseDTO;
import com.aoneng.rag.chat.dto.FeedbackDTO;
import com.aoneng.rag.chat.dto.MessageHistoryQuery;
import com.aoneng.rag.chat.dto.QaRecordPageQuery;
import com.aoneng.rag.chat.dto.UpdateConversationTitleDTO;
import com.aoneng.rag.chat.service.ConversationService;
import com.aoneng.rag.chat.service.RagChatService;
import com.aoneng.rag.chat.service.RagEvalService;
import com.aoneng.rag.chat.vo.ChunkVO;
import com.aoneng.rag.chat.vo.ConversationVO;
import com.aoneng.rag.chat.vo.CursorPageVO;
import com.aoneng.rag.chat.vo.EvalCaseVO;
import com.aoneng.rag.chat.vo.EvalRunResultVO;
import com.aoneng.rag.chat.vo.MessageVO;
import com.aoneng.rag.chat.vo.QaRecordDetailVO;
import com.aoneng.rag.chat.vo.QaRecordListVO;
import com.aoneng.rag.common.result.Result;
import com.aoneng.rag.application.repository.KbScope;
import com.aoneng.rag.application.repository.PlatformRepository;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ModelAttribute;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * RAG 对话入口。负责 HTTP 协议层面的请求校验和响应封装，
 * 所有对话流水线、会话管理和检索评测业务逻辑委托给
 * {@link RagChatService}、{@link ConversationService} 和 {@link RagEvalService}。
 *
 * <p>异常（{@code NoSuchElementException} → 404、{@code IllegalArgumentException} → 400）
 * 由 {@code GlobalExceptionHandler} 统一处理，控制器无需自行 try-catch。</p>
 */
@RestController
@RequestMapping("/api/v1/rag")
public class RagController {

    private final RagChatService chatService;
    private final ConversationService conversationService;
    private final RagEvalService evalService;

    public RagController(RagChatService chatService,
                         ConversationService conversationService,
                         RagEvalService evalService) {
        this.chatService = chatService;
        this.conversationService = conversationService;
        this.evalService = evalService;
    }

    // -------- 流式对话 (SSE) --------

    /**
     * RAG 流式问答接口。通过 SSE 返回实时生成的答案和引用信息。
     */
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<Object>> chat(@AuthenticationPrincipal String username,
                                              @Valid @RequestBody ChatDTO request) {
        KbScope scope = chatService.requireChatAccess(username);
        List<Long> permittedKbIds = chatService.permittedKbIds(scope, request.kbIds());
        PlatformRepository.ChatTurn turn = chatService.prepareTurn(scope.userId(),
                request.conversationId(), request.question(), permittedKbIds);
        return chatService.streamAnswer(scope, turn, username, request.question(), permittedKbIds);
    }

    // -------- 会话管理 --------

    @GetMapping("/conversations")
    public Result<CursorPageVO<ConversationVO>> conversations(
            @AuthenticationPrincipal String username,
            @Valid @ModelAttribute ConversationPageQuery query) {
        KbScope scope = chatService.requireChatAccess(username);
        return Result.ok(conversationService.conversations(scope.userId(), query.cursor(), query.pageSizeOrDefault()));
    }

    @PostMapping("/conversations")
    public Result<ConversationVO> createConversation(
            @AuthenticationPrincipal String username,
            @Valid @RequestBody CreateConversationDTO req) {
        KbScope scope = chatService.requireChatAccess(username);
        List<Long> selectedKbIds = chatService.permittedKbIds(scope, req.kbIds());
        return Result.ok(conversationService.createConversation(
                scope.userId(), req.title(), selectedKbIds));
    }

    @GetMapping("/conversations/{id}")
    public Result<ConversationVO> conversation(
            @AuthenticationPrincipal String username,
            @PathVariable long id) {
        KbScope scope = chatService.requireChatAccess(username);
        return Result.ok(conversationService.conversation(scope.userId(), id));
    }

    @RequestMapping(value = "/conversations/{id}", method = {RequestMethod.PUT, RequestMethod.PATCH})
    public Result<ConversationVO> updateConversation(
            @AuthenticationPrincipal String username,
            @PathVariable long id,
            @Valid @RequestBody UpdateConversationTitleDTO req) {
        KbScope scope = chatService.requireChatAccess(username);
        return Result.ok(conversationService.updateTitle(scope.userId(), id, req.title()));
    }

    @DeleteMapping("/conversations/{id}")
    public Result<Void> deleteConversation(
            @AuthenticationPrincipal String username,
            @PathVariable long id) {
        KbScope scope = chatService.requireChatAccess(username);
        conversationService.deleteConversation(scope.userId(), id);
        return Result.ok();
    }

    @GetMapping("/conversations/{id}/messages")
    public Result<CursorPageVO<MessageVO>> messages(
            @AuthenticationPrincipal String username,
            @PathVariable long id,
            @Valid @ModelAttribute MessageHistoryQuery query) {
        KbScope scope = chatService.requireChatAccess(username);
        return Result.ok(conversationService.messages(scope.userId(), id, query.before(), query.pageSizeOrDefault()));
    }

    // -------- 问答记录 --------

    @GetMapping("/records")
    public Result<QaRecordListVO> records(
            @AuthenticationPrincipal String username,
            @Valid @ModelAttribute QaRecordPageQuery query) {
        KbScope scope = chatService.requireChatAccess(username);
        return Result.ok(conversationService.qaRecords(scope.userId(), query.pageOrDefault(), query.pageSizeOrDefault()));
    }

    @GetMapping("/records/{id}")
    public Result<QaRecordDetailVO> record(
            @AuthenticationPrincipal String username,
            @PathVariable long id) {
        KbScope scope = chatService.requireChatAccess(username);
        return Result.ok(conversationService.qaRecord(scope.userId(), id));
    }

    @PostMapping("/feedback")
    public Result<Void> feedback(@AuthenticationPrincipal String username,
                                 @Valid @RequestBody FeedbackDTO req) {
        KbScope scope = chatService.requireChatAccess(username);
        int rating = req.rating() == null ? 0 : req.rating();
        conversationService.feedback(scope.userId(), req.qaRecordId(), rating, req.comment());
        return Result.ok();
    }

    @GetMapping("/chunks/{id}")
    public Result<ChunkVO> chunk(@AuthenticationPrincipal String username,
                                 @PathVariable long id) {
        chatService.requireChatAccess(username);
        return Result.ok(new ChunkVO(id, 0L, 0L, "向量库中的文档片段"));
    }

    // -------- 检索评测 --------

    @GetMapping("/evaluation/cases")
    public Result<List<EvalCaseVO>> retrievalEvaluationCases(
            @AuthenticationPrincipal String username) {
        chatService.requireAdmin(username);
        return Result.ok(conversationService.retrievalEvaluationCases());
    }

    @PostMapping("/evaluation/cases")
    public Result<EvalCaseVO> createRetrievalEvaluationCase(
            @AuthenticationPrincipal String username,
            @Valid @RequestBody CreateEvalCaseDTO request) {
        chatService.requireAdmin(username);
        return Result.ok(conversationService.createRetrievalEvalCase(request));
    }

    @PostMapping("/evaluation/cases/{id}/run")
    public Result<EvalRunResultVO> runRetrievalEvaluationCase(
            @AuthenticationPrincipal String username,
            @PathVariable long id) {
        KbScope scope = chatService.requireAdmin(username);
        return Result.ok(evalService.evaluate(id, scope));
    }
}
