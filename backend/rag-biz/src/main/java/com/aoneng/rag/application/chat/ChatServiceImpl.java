package com.aoneng.rag.application.chat;

import com.aoneng.rag.application.repository.KbScope;
import com.aoneng.rag.application.repository.PlatformRepository;
import com.aoneng.rag.application.retrieval.RetrievalService;
import com.aoneng.rag.common.exception.ForbiddenException;
import com.aoneng.rag.infra.config.RagSseProperties;
import com.aoneng.rag.infra.llm.LlmService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import com.aoneng.rag.evaluation.RagEvaluationService;
import com.aoneng.rag.observability.RagObservability;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * RAG 对话应用服务实现。
 * 响应式 RAG 流水线：准备对话轮次、构造带引用标记的模型上下文、流式返回模型回答、持久化完成轮次。
 */
@Service
public class ChatServiceImpl implements ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatServiceImpl.class);

    private final PlatformRepository repo;
    private final RetrievalService retrieval;
    private final LlmService llm;
    private final RagSseProperties sseProperties;
    private final Duration sseTimeout;
    private final RagEvaluationService evaluation;
    private final RagObservability observability;

    public ChatServiceImpl(PlatformRepository repo,
                           RetrievalService retrieval,
                           LlmService llm,
                           RagSseProperties sseProperties,
                           RagEvaluationService evaluation,
                           RagObservability observability) {
        this.repo = repo;
        this.retrieval = retrieval;
        this.llm = llm;
        this.sseProperties = sseProperties;
        this.sseTimeout = Duration.ofSeconds(sseProperties.timeoutSeconds());
        this.evaluation = evaluation;
        this.observability = observability;
    }

    @Override
    public KbScope requireChatAccess(String username) {
        KbScope scope = repo.kbScope(username);
        if (!scope.admin() && !scope.kbAccess()) {
            throw new ForbiddenException("当前角色未获知识库访问授权");
        }
        return scope;
    }

    @Override
    public KbScope requireAdmin(String username) {
        KbScope scope = repo.kbScope(username);
        if (!scope.admin()) {
            throw new ForbiddenException("仅系统管理员可以执行检索评测");
        }
        return scope;
    }

    @Override
    public List<Long> permittedKbIds(KbScope scope, List<Long> requestedKbIds) {
        List<Long> allowed = repo.accessibleBaseIds(scope);
        if (requestedKbIds == null || requestedKbIds.isEmpty()) return allowed;
        Set<Long> requested = new java.util.LinkedHashSet<>(requestedKbIds);
        return allowed.stream().filter(requested::contains).toList();
    }

    @Override
    public PlatformRepository.ChatTurn prepareTurn(long userId, Long conversationId, String question,
                                                   List<Long> permittedKbIds) {
        return repo.prepareChatTurn(userId, conversationId, question, permittedKbIds);
    }

    @Override
    public RetrievalService.RetrievalResult retrieve(String question, List<Long> permittedKbIds) {
        return retrieval.retrieve(question, permittedKbIds);
    }

    @Override
    public Flux<ServerSentEvent<Object>> streamAnswer(KbScope scope,
                                                       PlatformRepository.ChatTurn turn,
                                                       String question,
                                                       List<Long> permittedKbIds) {
        AtomicBoolean completed = new AtomicBoolean(false);
        return Mono.fromCallable(() -> retrieve(question, permittedKbIds))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(result -> {
                    ChatStreamPayload p = buildStreamPayload(result, question, scope, turn, permittedKbIds);
                    StringBuilder answer = new StringBuilder();
                    long llmStarted = System.nanoTime();

                    Flux<ServerSentEvent<Object>> init = Flux.just(
                            buildEvent("message", Map.of("choices",
                                    List.of(Map.of("index", 0, "delta", Map.of("role", "assistant"))))),
                            // 检索结果只是候选来源，等模型明确引用后再展示，避免无关文件提前出现在答案底部。
                            buildEvent("sources", List.of()));

                    Flux<ServerSentEvent<Object>> model = llm.streamChatFlux(question, p.modelContext())
                            .doOnNext(answer::append)
                            .doFinally(signal -> observability.record("llm.chat", System.nanoTime() - llmStarted,
                                    signal == reactor.core.publisher.SignalType.ON_COMPLETE,
                                    Map.of("conversation_id", turn.conversationId(), "signal", signal.name(),
                                            "answer_chars", answer.length(),
                                            "trace_id", String.valueOf(result.trace().getOrDefault("trace_id", "")))))
                            .map(delta -> buildEvent("message",
                                    Map.of("choices",
                                            List.of(Map.of("index", 0, "delta",
                                                    Map.of("content", delta))))));

                    Mono<ServerSentEvent<Object>> done = Mono.fromCallable(() -> {
                                String answerText = removeSourceMarkers(answer.toString());
                                Map<String, Object> donePayload = new LinkedHashMap<>(completeTurn(turn, scope, question, answerText,
                                        permittedKbIds, p.chunkIds(), p.citationsBySource(), result.trace()));
                                // 只返回模型实际标记的来源；检索候选不再作为引用展示。
                                donePayload.put("citations", publicCitations(groupCitations(
                                        citedSources(answer.toString(), p.citationsBySource()))));
                                completed.set(true);
                                return buildEvent("done", donePayload);
                            })
                            .subscribeOn(Schedulers.boundedElastic());

                    return init.concatWith(model).concatWith(done);
                })
                .timeout(sseTimeout)
                .onErrorResume(error -> {
                    if (completed.compareAndSet(false, true)) {
                        failTurn(turn, "闂瓟鐢熸垚澶辫触锛岃绋嶅悗閲嶈瘯");
                    }
                    log.warn("SSE 流式问答异常，conversationId={}, messageId={}, error={}",
                            turn.conversationId(), turn.assistantMessageId(), error.getMessage(), error);
                    return Flux.just(
                            buildEvent("error", Map.of(
                                    "message", "问答生成失败，请稍后重试",
                                    "conversationId", turn.conversationId(),
                                    "messageId", turn.assistantMessageId())));
                });
    }

    @Override
    public ChatStreamPayload buildStreamPayload(RetrievalService.RetrievalResult result,
                                                 String question,
                                                 KbScope scope,
                                                 PlatformRepository.ChatTurn turn,
                                                 List<Long> permittedKbIds) {
        StringBuilder context = new StringBuilder();
        Map<String, Map<String, Object>> citations = new LinkedHashMap<>();
        List<Long> chunkIds = new ArrayList<>();
        Set<Long> expandedParents = new java.util.LinkedHashSet<>();
        for (Map<String, Object> hit : result.hits()) {
            Map<String, Object> payload = payload(hit.get("payload"));
            String content = text(payload, "content", "text", "snippet");
            long docId = number(payload.get("doc_id"), 0L), kbId = number(payload.get("kb_id"), 0L);
            long parentId = number(payload.get("parent_id"), number(payload.get("parentId"), 0L));
            Map<String, Object> document = indexedDocument(docId, kbId);
            if (content.isBlank() || document == null) continue;
            long chunkId = number(payload.get("chunk_id"), number(hit.get("id"), 0L));
            if (isCiteableDocument(document, payload)) {
                citations.putIfAbsent(sourceKey(docId, chunkId), citation(hit, payload, content, document));
                context.append("[source:").append(docId).append('-').append(chunkId).append("]\n");
            }
            if (parentId <= 0) context.append(content).append('\n');
            if (chunkId > 0 && !chunkIds.contains(chunkId)) chunkIds.add(chunkId);
            if (parentId > 0 && expandedParents.add(parentId)) {
                Map<String, Object> parent = repo.parentChunk(parentId);
                String parentContent = validParentContent(parent, docId, kbId);
                if (!parentContent.isBlank()) context.append(parentContent).append('\n');
                else context.append(content).append('\n');
            }
        }
        List<Map<String, Object>> sources = publicCitations(groupCitations(new ArrayList<>(citations.values())))
                .stream()
                .limit(sseProperties.maxCitations())
                .toList();
        return new ChatStreamPayload(context.toString(), sources, citations, chunkIds);
    }

    @Override
    public Map<String, Object> completeTurn(PlatformRepository.ChatTurn turn,
                                             KbScope scope,
                                             String question,
                                             String answerText,
                                             List<Long> permittedKbIds,
                                             List<Long> chunkIds,
                                             Map<String, Map<String, Object>> citationsBySource,
                                             Map<String, Object> trace) {
        long id = repo.completeChatTurn(turn, scope.userId(), question, answerText, permittedKbIds, chunkIds,
                citedSources(answerText, citationsBySource), 0, trace);
        evaluation.enqueue(id);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("recordId", id);
        result.put("conversationId", turn.conversationId());
        result.put("userMessageId", turn.userMessageId());
        result.put("messageId", turn.assistantMessageId());
        return result;
    }

    @Override
    public void failTurn(PlatformRepository.ChatTurn turn, String message) {
        repo.failChatTurn(turn, message);
    }

    @Override
    public String removeSourceMarkers(String answer) {
        return answer == null ? "" : SOURCE_MARKER.matcher(answer).replaceAll("").replaceAll("[ \\t]+\\n", "\n").trim();
    }

    private static ServerSentEvent<Object> buildEvent(String name, Object data) {
        return ServerSentEvent.builder(data).event(name).build();
    }

    private Map<String, Object> citation(Map<String, Object> hit, Map<String, Object> payload, String content, Map<String, Object> document) {
        Map<String, Object> citation = new LinkedHashMap<>();
        citation.put("chunkId", number(payload.get("chunk_id"), number(hit.get("id"), 0L)));
        long parentId = number(payload.get("parent_id"), number(payload.get("parentId"), 0L));
        if (parentId > 0) citation.put("parentId", parentId);
        citation.put("docId", number(payload.get("doc_id"), 0L));
        citation.put("kbId", number(payload.get("kb_id"), 0L));
        citation.put("fileName", fileName(payload, document));
        citation.put("snippet", content);
        Double score = decimal(hit.get("score"));
        if (score != null) citation.put("score", score);
        return citation;
    }

    private List<Map<String, Object>> citedSources(String answer, Map<String, Map<String, Object>> citationsBySource) {
        if (answer == null || answer.isBlank() || citationsBySource.isEmpty()) return List.of();
        List<Map<String, Object>> citations = new ArrayList<>();
        java.util.regex.Matcher matcher = SOURCE_MARKER.matcher(answer);
        while (matcher.find() && citations.size() < sseProperties.maxCitations()) {
            String key = sourceKey(Long.parseLong(matcher.group(1)), Long.parseLong(matcher.group(2)));
            Map<String, Object> citation = citationsBySource.get(key);
            if (citation != null && !citations.contains(citation)) citations.add(citation);
        }
        // 没有明确来源标记时不保存任何引用，避免把未支撑答案的检索候选误展示为来源。
        return citations;
    }

    private List<Map<String, Object>> groupCitations(List<Map<String, Object>> citations) {
        Map<String, CitationGroup> groups = new LinkedHashMap<>();
        for (Map<String, Object> citation : citations) {
            long docId = number(citation.get("docId"), 0L);
            long kbId = number(citation.get("kbId"), 0L);
            String fileName = stringValue(citation.get("fileName"));
            if (docId <= 0 || kbId <= 0 || fileName.isBlank()) continue;
            String key = docId + ":" + kbId + ":" + fileName;
            groups.computeIfAbsent(key, ignored -> new CitationGroup(citation));
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (CitationGroup group : groups.values()) {
            result.add(new LinkedHashMap<>(group.first));
        }
        return result;
    }

    private List<Map<String, Object>> publicCitations(List<Map<String, Object>> citations) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> citation : citations) {
            String fileName = stringValue(citation.get("fileName"));
            if (fileName.isBlank()) continue;
            Map<String, Object> view = new LinkedHashMap<>();
            view.put("fileName", fileName);
            copyCitationField(citation, view, "chunkId");
            copyCitationField(citation, view, "parentId");
            copyCitationField(citation, view, "docId");
            copyCitationField(citation, view, "kbId");
            copyCitationField(citation, view, "snippet");
            copyCitationField(citation, view, "score");
            result.add(view);
        }
        return result;
    }

    private String sourceKey(long docId, long chunkId) {
        return docId + ":" + chunkId;
    }

    private static final java.util.regex.Pattern SOURCE_MARKER =
            java.util.regex.Pattern.compile("[【\\[](?:来源|source)[：:]\\s*(\\d+)\\s*-\\s*(\\d+)[】\\]]",
                    java.util.regex.Pattern.CASE_INSENSITIVE);

    private static final class CitationGroup {
        private final Map<String, Object> first;

        private CitationGroup(Map<String, Object> first) {
            this.first = first;
        }
    }

    private boolean isCiteableDocument(Map<String, Object> document, Map<String, Object> payload) {
        return document != null && !fileName(payload, document).isBlank();
    }

    private void copyCitationField(Map<String, Object> source, Map<String, Object> target, String key) {
        Object value = source.get(key);
        if (value != null) target.put(key, value);
    }

    private String validParentContent(Map<String, Object> parent, long docId, long kbId) {
        if (parent == null) return "";
        long parentDocId = number(parent.get("docId"), number(parent.get("doc_id"), 0L));
        long parentKbId = number(parent.get("kbId"), number(parent.get("kb_id"), 0L));
        return parentDocId == docId && parentKbId == kbId ? text(parent, "content", "text") : "";
    }

    private Map<String, Object> indexedDocument(long docId, long kbId) {
        if (docId <= 0 || kbId <= 0) return null;
        try {
            Map<String, Object> document = repo.doc(docId);
            boolean belongs = number(document.get("kbId"), 0L) == kbId;
            boolean indexed = "SUCCESS".equals(document.get("parseStatus"))
                    && ("INDEXED".equals(document.get("chunkStatus")) || "PARTIAL".equals(document.get("chunkStatus")));
            return belongs && indexed ? document : null;
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

    @SuppressWarnings("unchecked")
    private Map<String, Object> payload(Object raw) {
        return raw instanceof Map<?, ?> values ? (Map<String, Object>) values : Map.of();
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
        if (value instanceof Number n) return n.longValue();
        try {
            return value == null ? fallback : Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private Double decimal(Object value) {
        if (value instanceof Number n) return n.doubleValue();
        try {
            return value == null ? null : Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
