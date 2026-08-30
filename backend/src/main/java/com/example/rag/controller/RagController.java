package com.example.rag.controller;

import com.example.rag.service.DashScopeService;
import com.example.rag.service.PlatformRepository;
import com.example.rag.service.RagRetrievalService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.*;

@RestController
@RequestMapping("/api/rag")
public class RagController {
    private static final java.util.regex.Pattern SOURCE_MARKER = java.util.regex.Pattern.compile("[【\\[]来源[：:]\\s*(\\d+)\\s*-\\s*(\\d+)[】\\]]");
    private static final Set<String> NO_HIT_ANSWER_MARKERS = Set.of(
            "知识库中未提及", "未找到相关", "没有相关", "无相关信息", "上下文中没有", "上下文未包含", "无法根据提供的知识库", "未提供相关", "请提供具体问题"
    );

    private final DashScopeService dash;
    private final PlatformRepository repo;
    private final RagRetrievalService retrieval;

    public RagController(DashScopeService dash, PlatformRepository repo, RagRetrievalService retrieval) {
        this.dash = dash;
        this.repo = repo;
        this.retrieval = retrieval;
    }

    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@AuthenticationPrincipal String username, @RequestBody Map<String, Object> req) {
        PlatformRepository.KbScope scope = requireChatAccess(username);
        String question = stringValue(req.get("question"));
        if (question.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "问题不能为空");

        List<Long> allowedKbIds = repo.accessibleBaseIds(scope);
        Set<Long> requestedKbIds = requestedKbIds(req.get("kbIds"));
        List<Long> permittedKbIds = requestedKbIds.isEmpty()
                ? allowedKbIds
                : allowedKbIds.stream().filter(requestedKbIds::contains).toList();
        Long conversationId = nullableLong(req.get("conversationId"));
        PlatformRepository.ChatTurn turn;
        try {
            turn = repo.prepareChatTurn(scope.userId(), conversationId, question, permittedKbIds);
        } catch (NoSuchElementException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage(), e);
        }

        SseEmitter emitter = new SseEmitter(0L);
        new Thread(() -> streamAnswer(emitter, scope, turn, question, permittedKbIds), "rag-chat-" + turn.assistantMessageId()).start();
        return emitter;
    }

    @GetMapping("/conversations")
    public Map<String, Object> conversations(@AuthenticationPrincipal String username,
                                             @RequestParam(required = false) String cursor,
                                             @RequestParam(defaultValue = "20") int pageSize) {
        PlatformRepository.KbScope scope = requireChatAccess(username);
        try {
            return repo.conversations(scope.userId(), cursor, pageSize);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        }
    }

    @PostMapping("/conversations")
    public Map<String, Object> createConversation(@AuthenticationPrincipal String username, @RequestBody Map<String, Object> req) {
        PlatformRepository.KbScope scope = requireChatAccess(username);
        List<Long> selectedKbIds = permittedKbIds(scope, req.get("kbIds"));
        try {
            return repo.createConversation(scope.userId(), stringValue(req.get("title")), selectedKbIds);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        }
    }

    @GetMapping("/conversations/{id}")
    public Map<String, Object> conversation(@AuthenticationPrincipal String username, @PathVariable long id) {
        PlatformRepository.KbScope scope = requireChatAccess(username);
        try {
            return repo.conversation(scope.userId(), id);
        } catch (NoSuchElementException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage(), e);
        }
    }

    @PatchMapping("/conversations/{id}")
    public Map<String, Object> updateConversation(@AuthenticationPrincipal String username, @PathVariable long id, @RequestBody Map<String, Object> req) {
        PlatformRepository.KbScope scope = requireChatAccess(username);
        try {
            return repo.updateConversationTitle(scope.userId(), id, stringValue(req.get("title")));
        } catch (NoSuchElementException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage(), e);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        }
    }

    @DeleteMapping("/conversations/{id}")
    public Map<String, Object> deleteConversation(@AuthenticationPrincipal String username, @PathVariable long id) {
        PlatformRepository.KbScope scope = requireChatAccess(username);
        try {
            repo.deleteConversation(scope.userId(), id);
            return Map.of("id", id, "deleted", true);
        } catch (NoSuchElementException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage(), e);
        }
    }

    @GetMapping("/conversations/{id}/messages")
    public Map<String, Object> messages(@AuthenticationPrincipal String username, @PathVariable long id,
                                        @RequestParam(required = false) String before,
                                        @RequestParam(defaultValue = "30") int pageSize) {
        PlatformRepository.KbScope scope = requireChatAccess(username);
        try {
            return repo.messages(scope.userId(), id, before, pageSize);
        } catch (NoSuchElementException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage(), e);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        }
    }

    @GetMapping("/records")
    public Map<String, Object> records(@AuthenticationPrincipal String username,
                                       @RequestParam(defaultValue = "1") int page,
                                       @RequestParam(defaultValue = "20") int pageSize) {
        PlatformRepository.KbScope scope = requireChatAccess(username);
        return repo.qaRecords(scope.userId(), page, pageSize);
    }

    @GetMapping("/records/{id}")
    public Map<String, Object> record(@AuthenticationPrincipal String username, @PathVariable long id) {
        PlatformRepository.KbScope scope = requireChatAccess(username);
        try {
            return repo.qaRecord(scope.userId(), id);
        } catch (NoSuchElementException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage(), e);
        }
    }

    @GetMapping("/evaluation/cases")
    public List<Map<String, Object>> retrievalEvaluationCases(@AuthenticationPrincipal String username) {
        requireAdmin(username);
        return repo.retrievalEvalCases();
    }

    @PostMapping("/evaluation/cases")
    public Map<String, Object> createRetrievalEvaluationCase(@AuthenticationPrincipal String username,
                                                             @RequestBody Map<String, Object> request) {
        requireAdmin(username);
        try {
            return repo.createRetrievalEvalCase(request);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        }
    }

    @PostMapping("/evaluation/cases/{id}/run")
    public Map<String, Object> runRetrievalEvaluationCase(@AuthenticationPrincipal String username, @PathVariable long id) {
        PlatformRepository.KbScope scope = requireAdmin(username);
        Map<String, Object> evalCase;
        try {
            evalCase = repo.retrievalEvalCase(id);
        } catch (NoSuchElementException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage(), e);
        }
        String question = String.valueOf(evalCase.getOrDefault("question", ""));
        RagRetrievalService.RetrievalResult retrievalResult = retrieval.retrieve(question, repo.accessibleBaseIds(scope));
        List<Map<String, Object>> hits = retrievalResult.hits();
        Set<Long> expected = toLongSet(evalCase.get("expectedChunkIds"));
        List<Long> returnedOrdered = new ArrayList<>();
        Set<Long> returned = new LinkedHashSet<>();
        for (Map<String, Object> hit : hits) {
            Map<String, Object> payload = payload(hit.get("payload"));
            long chunkId = number(payload.get("chunk_id"), number(hit.get("id"), 0L));
            if (chunkId > 0 && returned.add(chunkId)) returnedOrdered.add(chunkId);
        }
        Set<Long> matched = new LinkedHashSet<>(returned);
        matched.retainAll(expected);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("caseId", id);
        result.put("question", question);
        result.put("expectedChunkIds", expected);
        result.put("returnedChunkIds", returned);
        result.put("recallAtK", expected.isEmpty() ? null : (double) matched.size() / expected.size());
        int firstRelevant = -1;
        for (int index = 0; index < returnedOrdered.size(); index++) {
            if (expected.contains(returnedOrdered.get(index))) {
                firstRelevant = index + 1;
                break;
            }
        }
        result.put("mrrAtK", firstRelevant < 0 ? 0D : 1D / firstRelevant);
        double dcg = 0D;
        for (int index = 0; index < returnedOrdered.size(); index++) {
            if (expected.contains(returnedOrdered.get(index))) dcg += 1D / (Math.log(index + 2) / Math.log(2));
        }
        double ideal = 0D;
        for (int index = 0; index < Math.min(expected.size(), returnedOrdered.size()); index++)
            ideal += 1D / (Math.log(index + 2) / Math.log(2));
        result.put("nDcgAtK", ideal == 0D ? 0D : dcg / ideal);
        result.put("hit", !matched.isEmpty());
        result.put("retrieval", retrievalResult.trace());
        return result;
    }

    @PostMapping("/feedback")
    public Map<String, Object> feedback(@AuthenticationPrincipal String username, @RequestBody Map<String, Object> body) {
        PlatformRepository.KbScope scope = requireChatAccess(username);
        Long recordId = nullableLong(body.get("qaRecordId"));
        if (recordId == null || !repo.ownsQaRecord(scope.userId(), recordId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "问答记录不存在或无权访问");
        }
        int rating = nullableLong(body.get("rating")) == null ? 0 : nullableLong(body.get("rating")).intValue();
        if (rating != 1 && rating != -1)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "评分必须为 1 或 -1");
        repo.feedback(recordId, scope.userId(), rating, stringValue(body.get("comment")));
        return Map.of("success", true, "qaRecordId", recordId, "rating", rating);
    }

    @GetMapping("/chunks/{id}")
    public Map<String, Object> chunk(@AuthenticationPrincipal String username, @PathVariable long id) {
        requireChatAccess(username);
        return Map.of("chunkId", id, "content", "向量库中的文档片段", "docId", 0, "kbId", 0);
    }

    private void streamAnswer(SseEmitter emitter, PlatformRepository.KbScope scope, PlatformRepository.ChatTurn turn,
                              String question, List<Long> permittedKbIds) {
        long start = System.currentTimeMillis();
        try {
            RagRetrievalService.RetrievalResult retrievalResult = retrieval.retrieve(question, permittedKbIds);
            List<Map<String, Object>> hits = retrievalResult.hits();
            StringBuilder context = new StringBuilder();
            Map<String, Map<String, Object>> citationsBySource = new LinkedHashMap<>();
            List<Long> retrievedChunkIds = new ArrayList<>();
            for (Map<String, Object> hit : hits) {
                Map<String, Object> payload = payload(hit.get("payload"));
                String content = text(payload, "content", "text", "snippet");
                long docId = number(payload.get("doc_id"), 0L);
                long kbId = number(payload.get("kb_id"), 0L);
                Map<String, Object> document = indexedDocument(docId, kbId);
                if (content.isBlank() || document == null) continue;
                String sourceKey = null;
                if (isCiteablePdf(document, payload)) {
                    long pageNo = number(payload.get("page_no"), number(payload.get("pageNo"), 0L));
                    sourceKey = sourceKey(docId, pageNo);
                    citationsBySource.putIfAbsent(sourceKey, citation(hit, payload, content, document));
                    context.append("【来源:").append(docId).append('-').append(pageNo).append("】\n");
                }
                context.append(content).append('\n');
                long chunkId = number(payload.get("chunk_id"), number(hit.get("id"), 0L));
                if (chunkId > 0 && !retrievedChunkIds.contains(chunkId)) retrievedChunkIds.add(chunkId);
            }
            String rawAnswer = dash.chat(question, context.toString());
            List<Map<String, Object>> pageCitations = citedSources(rawAnswer, citationsBySource);
            List<Map<String, Object>> citations = publicCitations(groupCitations(pageCitations));
            String answer = removeSourceMarkers(rawAnswer);
            boolean hasGroundedAnswer = hasGroundedAnswer(answer);
            if (hasGroundedAnswer && !citations.isEmpty()) {
                emitter.send(SseEmitter.event().name("citations").data(citations));
            }
            for (String part : answer.split("(?<=[。！？.!?])")) {
                if (!part.isBlank()) {
                    emitter.send(SseEmitter.event().name("chunk").data(Map.of("delta", part)));
                    Thread.sleep(20);
                }
            }
            int latency = (int) (System.currentTimeMillis() - start);
            long recordId = repo.completeChatTurn(turn, scope.userId(), question, answer, permittedKbIds, retrievedChunkIds,
                    hasGroundedAnswer ? pageCitations : List.of(), latency, retrievalResult.trace());
            emitter.send(SseEmitter.event().name("done").data(Map.of(
                    "recordId", recordId,
                    "conversationId", turn.conversationId(),
                    "userMessageId", turn.userMessageId(),
                    "messageId", turn.assistantMessageId(),
                    "retrieval", retrievalResult.trace()
            )));
            emitter.complete();
        } catch (Exception e) {
            repo.failChatTurn(turn, "问答生成失败，请稍后重试");
            try {
                emitter.send(SseEmitter.event().name("error").data(Map.of(
                        "message", "问答生成失败，请稍后重试",
                        "conversationId", turn.conversationId(),
                        "messageId", turn.assistantMessageId()
                )));
            } catch (Exception ignored) {
                // The client can close an SSE request before the server reports its terminal event.
            }
            emitter.completeWithError(e);
        }
    }

    private PlatformRepository.KbScope requireChatAccess(String username) {
        PlatformRepository.KbScope scope = repo.kbScope(username);
        if (!scope.admin() && !scope.kbAccess()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前角色未获知识库访问授权");
        }
        return scope;
    }

    private PlatformRepository.KbScope requireAdmin(String username) {
        PlatformRepository.KbScope scope = repo.kbScope(username);
        if (!scope.admin()) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "仅系统管理员可以执行检索评测");
        return scope;
    }

    private List<Long> permittedKbIds(PlatformRepository.KbScope scope, Object rawKbIds) {
        Set<Long> requested = requestedKbIds(rawKbIds);
        List<Long> allowed = repo.accessibleBaseIds(scope);
        return requested.isEmpty() ? allowed : allowed.stream().filter(requested::contains).toList();
    }

    private Set<Long> requestedKbIds(Object raw) {
        if (!(raw instanceof List<?> list)) return Set.of();
        Set<Long> ids = new LinkedHashSet<>();
        for (Object item : list) {
            Long id = nullableLong(item);
            if (id != null && id > 0) ids.add(id);
        }
        return ids;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> payload(Object raw) {
        return raw instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    private Map<String, Object> citation(Map<String, Object> hit, Map<String, Object> payload, String content, Map<String, Object> document) {
        long docId = number(payload.get("doc_id"), 0L);
        Map<String, Object> citation = new LinkedHashMap<>();
        citation.put("chunkId", number(payload.get("chunk_id"), number(hit.get("id"), 0L)));
        citation.put("docId", docId);
        citation.put("kbId", number(payload.get("kb_id"), 0L));
        citation.put("fileName", fileName(payload, document));
        citation.put("snippet", content);
        Long pageNo = number(payload.get("page_no"), number(payload.get("pageNo"), 0L));
        if (pageNo > 0) citation.put("pageNo", pageNo);
        Double score = decimal(hit.get("score"));
        if (score != null) citation.put("score", score);
        return citation;
    }

    private List<Map<String, Object>> citedSources(String answer, Map<String, Map<String, Object>> citationsBySource) {
        if (answer == null || answer.isBlank() || citationsBySource.isEmpty()) return List.of();
        List<Map<String, Object>> citations = new ArrayList<>();
        java.util.regex.Matcher matcher = SOURCE_MARKER.matcher(answer);
        while (matcher.find() && citations.size() < 3) {
            String key = sourceKey(Long.parseLong(matcher.group(1)), Long.parseLong(matcher.group(2)));
            Map<String, Object> citation = citationsBySource.get(key);
            if (citation != null && !citations.contains(citation)) citations.add(citation);
        }
        return citations;
    }

    private List<Map<String, Object>> groupCitations(List<Map<String, Object>> citations) {
        Map<String, CitationGroup> groups = new LinkedHashMap<>();
        for (Map<String, Object> citation : citations) {
            long docId = number(citation.get("docId"), 0L);
            long kbId = number(citation.get("kbId"), 0L);
            long pageNo = number(citation.get("pageNo"), 0L);
            String fileName = stringValue(citation.get("fileName"));
            if (docId <= 0 || kbId <= 0 || pageNo <= 0 || fileName.isBlank()) continue;
            String key = docId + ":" + kbId + ":" + fileName;
            groups.computeIfAbsent(key, ignored -> new CitationGroup(citation)).pages.add(pageNo);
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (CitationGroup group : groups.values()) {
            List<Long> pages = group.pages.stream().sorted().toList();
            if (pages.isEmpty()) continue;
            Map<String, Object> citation = new LinkedHashMap<>(group.first);
            citation.put("pageNo", pages.get(0));
            citation.put("pageNos", pages);
            result.add(citation);
        }
        return result;
    }

    private List<Map<String, Object>> publicCitations(List<Map<String, Object>> citations) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> citation : citations) {
            String fileName = stringValue(citation.get("fileName"));
            long pageNo = number(citation.get("pageNo"), 0L);
            Object pageNos = citation.get("pageNos");
            if (fileName.isBlank() || pageNo <= 0 || !(pageNos instanceof List<?> pages) || pages.isEmpty()) continue;
            Map<String, Object> view = new LinkedHashMap<>();
            view.put("fileName", fileName);
            view.put("pageNo", pageNo);
            view.put("pageNos", pages);
            result.add(view);
        }
        return result;
    }

    private String removeSourceMarkers(String answer) {
        return answer == null ? "" : SOURCE_MARKER.matcher(answer).replaceAll("").replaceAll("[ \\t]+\\n", "\\n").trim();
    }

    private String sourceKey(long docId, long pageNo) {
        return docId + ":" + pageNo;
    }

    private static final class CitationGroup {
        private final Map<String, Object> first;
        private final Set<Long> pages = new TreeSet<>();

        private CitationGroup(Map<String, Object> first) {
            this.first = first;
        }
    }

    private boolean isCiteablePdf(Map<String, Object> document, Map<String, Object> payload) {
        String fileType = String.valueOf(document.getOrDefault("fileType", "")).trim();
        if (!"pdf".equalsIgnoreCase(fileType)) return false;
        long pageNo = number(payload.get("page_no"), number(payload.get("pageNo"), 0L));
        return pageNo > 0 && pageNo <= 100_000;
    }

    private Map<String, Object> indexedDocument(long docId, long kbId) {
        if (docId <= 0 || kbId <= 0) return null;
        try {
            Map<String, Object> document = repo.doc(docId);
            boolean belongsToKnowledgeBase = number(document.get("kbId"), 0L) == kbId;
            boolean indexed = "SUCCESS".equals(document.get("parseStatus"))
                    && ("INDEXED".equals(document.get("chunkStatus")) || "PARTIAL".equals(document.get("chunkStatus")));
            return belongsToKnowledgeBase && indexed ? document : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private String fileName(Map<String, Object> payload, Map<String, Object> document) {
        String name = text(payload, "file_name", "fileName", "filename");
        if (!name.isBlank()) return name;
        String documentName = String.valueOf(document.getOrDefault("fileName", "")).trim();
        return documentName.isBlank() ? "知识库文档" : documentName;
    }

    private boolean hasGroundedAnswer(String answer) {
        if (answer == null || answer.isBlank()) return false;
        String normalized = answer.replaceAll("\\s+", "");
        return NO_HIT_ANSWER_MARKERS.stream().noneMatch(normalized::contains);
    }

    private String text(Map<String, Object> values, String... keys) {
        for (String key : keys) {
            Object value = values.get(key);
            if (value != null && !String.valueOf(value).isBlank()) return String.valueOf(value).trim();
        }
        return "";
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private long number(Object value, long fallback) {
        Long parsed = nullableLong(value);
        return parsed == null ? fallback : parsed;
    }

    private Set<Long> toLongSet(Object raw) {
        if (!(raw instanceof Collection<?> values)) return Set.of();
        Set<Long> result = new LinkedHashSet<>();
        for (Object value : values) {
            long id = number(value, 0L);
            if (id > 0) result.add(id);
        }
        return result;
    }

    private Long nullableLong(Object value) {
        if (value instanceof Number number) return number.longValue();
        try {
            String raw = value == null ? "" : String.valueOf(value).trim();
            return raw.isBlank() ? null : Long.parseLong(raw);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private Double decimal(Object value) {
        if (value instanceof Number number) return number.doubleValue();
        try {
            return value == null ? null : Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
