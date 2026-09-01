package com.example.rag.chat.controller;

import com.example.rag.chat.dto.ChatRequest;
import com.example.rag.chat.dto.CreateConversationRequest;
import com.example.rag.chat.dto.CreateEvalCaseRequest;
import com.example.rag.chat.dto.FeedbackRequest;
import com.example.rag.chat.dto.UpdateConversationTitleRequest;
import com.example.rag.chat.service.ConversationService;
import com.example.rag.chat.service.RagChatService;
import com.example.rag.chat.service.RagEvalService;
import com.example.rag.common.result.Result;
import com.example.rag.service.PlatformRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * RAG 对话入口。负责 HTTP 协议层面的请求校验和响应封装，
 * 所有对话流水线、会话管理和检索评测业务逻辑委托给
 * {@link RagChatService}、{@link ConversationService} 和 {@link RagEvalService}。
 */
@RestController
@RequestMapping("/api/rag")
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
     *
     * @param username 当前登录用户名
     * @param req     问答请求（会话 ID、问题、可选知识库 ID 列表）
     * @return SSE 事件流
     */
    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<Object>> chat(@AuthenticationPrincipal String username,
                                             @Valid @RequestBody ChatRequest req) {
        PlatformRepository.KbScope scope = chatService.requireChatAccess(username);
        List<Long> permittedKbIds = chatService.permittedKbIds(scope, req.kbIds());
        PlatformRepository.ChatTurn turn;
        try {
            turn = chatService.prepareTurn(scope.userId(), req.conversationId(), req.question(), permittedKbIds);
        } catch (NoSuchElementException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage(), e);
        }
        return chatService.streamAnswer(scope, turn, req.question(), permittedKbIds);
    }

    // -------- 会话管理 --------

    /**
     * 获取当前用户的会话列表（游标分页）。
     *
     * @param username  当前登录用户名
     * @param cursor   游标（上一页最后一条记录的 ID）
     * @param pageSize 每页数量
     * @return 会话列表
     */
    @GetMapping("/conversations")
    public Result<List<Map<String, Object>>> conversations(
            @AuthenticationPrincipal String username,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int pageSize) {
        PlatformRepository.KbScope scope = chatService.requireChatAccess(username);
        try {
            return Result.ok(conversationService.conversations(scope.userId(), cursor, pageSize));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        }
    }

    /**
     * 创建新会话。
     *
     * @param username 当前登录用户名
     * @param req     创建会话请求（标题、可选知识库 ID 列表）
     * @return 新创建的会话信息
     */
    @PostMapping("/conversations")
    public Result<Map<String, Object>> createConversation(
            @AuthenticationPrincipal String username,
            @Valid @RequestBody CreateConversationRequest req) {
        PlatformRepository.KbScope scope = chatService.requireChatAccess(username);
        List<Long> selectedKbIds = chatService.permittedKbIds(scope, req.kbIds());
        try {
            return Result.ok(conversationService.createConversation(
                    scope.userId(), req.title(), selectedKbIds));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        }
    }

    /**
     * 获取会话详情。
     *
     * @param username 当前登录用户名
     * @param id      会话 ID
     * @return 会话详情
     */
    @GetMapping("/conversations/{id}")
    public Result<Map<String, Object>> conversation(
            @AuthenticationPrincipal String username,
            @PathVariable long id) {
        PlatformRepository.KbScope scope = chatService.requireChatAccess(username);
        try {
            return Result.ok(conversationService.conversation(scope.userId(), id));
        } catch (NoSuchElementException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage(), e);
        }
    }

    /**
     * 更新会话标题。
     *
     * @param username 当前登录用户名
     * @param id      会话 ID
     * @param req     更新内容（标题）
     * @return 更新后的会话信息
     */
    @PatchMapping("/conversations/{id}")
    public Result<Map<String, Object>> updateConversation(
            @AuthenticationPrincipal String username,
            @PathVariable long id,
            @Valid @RequestBody UpdateConversationTitleRequest req) {
        PlatformRepository.KbScope scope = chatService.requireChatAccess(username);
        try {
            return Result.ok(conversationService.updateTitle(scope.userId(), id, req.title()));
        } catch (NoSuchElementException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage(), e);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        }
    }

    /**
     * 删除会话。
     *
     * @param username 当前登录用户名
     * @param id      会话 ID
     * @return 空响应
     */
    @DeleteMapping("/conversations/{id}")
    public Result<Void> deleteConversation(
            @AuthenticationPrincipal String username,
            @PathVariable long id) {
        PlatformRepository.KbScope scope = chatService.requireChatAccess(username);
        try {
            conversationService.deleteConversation(scope.userId(), id);
            return Result.ok();
        } catch (NoSuchElementException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage(), e);
        }
    }

    /**
     * 获取会话消息历史。
     *
     * @param username  当前登录用户名
     * @param id       会话 ID
     * @param before   游标（上一页最后一条消息的 ID）
     * @param pageSize 每页数量
     * @return 消息历史
     */
    @GetMapping("/conversations/{id}/messages")
    public Result<Map<String, Object>> messages(
            @AuthenticationPrincipal String username,
            @PathVariable long id,
            @RequestParam(required = false) String before,
            @RequestParam(defaultValue = "30") int pageSize) {
        PlatformRepository.KbScope scope = chatService.requireChatAccess(username);
        try {
            return Result.ok(conversationService.messages(scope.userId(), id, before, pageSize));
        } catch (NoSuchElementException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage(), e);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        }
    }

    // -------- 问答记录 --------

    /**
     * 获取问答记录列表（分页）。
     *
     * @param username 当前登录用户名
     * @param page    页码
     * @param pageSize 每页数量
     * @return 问答记录列表
     */
    @GetMapping("/records")
    public Result<Map<String, Object>> records(
            @AuthenticationPrincipal String username,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        PlatformRepository.KbScope scope = chatService.requireChatAccess(username);
        return Result.ok(conversationService.qaRecords(scope.userId(), page, pageSize));
    }

    /**
     * 获取问答记录详情。
     *
     * @param username 当前登录用户名
     * @param id      记录 ID
     * @return 问答记录详情
     */
    @GetMapping("/records/{id}")
    public Result<Map<String, Object>> record(
            @AuthenticationPrincipal String username,
            @PathVariable long id) {
        PlatformRepository.KbScope scope = chatService.requireChatAccess(username);
        try {
            return Result.ok(conversationService.qaRecord(scope.userId(), id));
        } catch (NoSuchElementException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage(), e);
        }
    }

    /**
     * 提交问答反馈。
     *
     * @param username 当前登录用户名
     * @param req     反馈内容（记录 ID、评分、评语）
     * @return 空响应
     */
    @PostMapping("/feedback")
    public Result<Void> feedback(@AuthenticationPrincipal String username,
                                 @Valid @RequestBody FeedbackRequest req) {
        PlatformRepository.KbScope scope = chatService.requireChatAccess(username);
        try {
            int rating = req.rating() == null ? 0 : req.rating();
            conversationService.feedback(scope.userId(), req.qaRecordId(), rating, req.comment());
            return Result.ok();
        } catch (NoSuchElementException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage(), e);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        }
    }

    /**
     * 获取文档片段详情（引用来源）。
     *
     * @param id 片段 ID
     * @return 片段内容
     */
    @GetMapping("/chunks/{id}")
    public Result<Map<String, Object>> chunk(@PathVariable long id) {
        return Result.ok(Map.<String, Object>of("chunkId", id, "content", "向量库中的文档片段", "docId", 0, "kbId", 0));
    }

    // -------- 检索评测 --------

    /**
     * 获取检索评测用例列表（仅管理员）。
     *
     * @param username 当前登录用户名（须为管理员）
     * @return 评测用例列表
     */
    @GetMapping("/evaluation/cases")
    public Result<List<Map<String, Object>>> retrievalEvaluationCases(
            @AuthenticationPrincipal String username) {
        chatService.requireAdmin(username);
        return Result.ok(conversationService.retrievalEvaluationCases());
    }

    /**
     * 创建检索评测用例（仅管理员）。
     *
     * @param username 当前登录用户名（须为管理员）
     * @param request 创建内容（查询、预期文档 ID）
     * @return 创建的评测用例
     */
    @PostMapping("/evaluation/cases")
    public Result<Map<String, Object>> createRetrievalEvaluationCase(
            @AuthenticationPrincipal String username,
            @Valid @RequestBody CreateEvalCaseRequest request) {
        chatService.requireAdmin(username);
        try {
            return Result.ok(conversationService.createRetrievalEvalCase(request));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        }
    }

    /**
     * 运行检索评测用例（仅管理员）。
     *
     * @param username 当前登录用户名（须为管理员）
     * @param id      评测用例 ID
     * @return 评测结果
     */
    @PostMapping("/evaluation/cases/{id}/run")
    public Result<Map<String, Object>> runRetrievalEvaluationCase(
            @AuthenticationPrincipal String username,
            @PathVariable long id) {
        PlatformRepository.KbScope scope = chatService.requireAdmin(username);
        return Result.ok(evalService.evaluate(id, scope));
    }
}
