package com.aoneng.rag.application.retrieval;

import com.aoneng.rag.application.repository.PlatformRepository;
import com.aoneng.rag.infra.llm.LlmService;
import com.aoneng.rag.infra.vector.Bm25SparseVectorizer;
import com.aoneng.rag.infra.vector.VectorStore;
import com.aoneng.rag.observability.RagObservability;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.UUID;

/**
 * 检索应用服务实现。
 * 混合密集向量 + 关键词检索，使用 RRF 融合，可选地应用 DashScope 重排。
 */
@Service
public class RetrievalServiceImpl implements RetrievalService {

    private final LlmService llm;
    private final PlatformRepository repo;
    private final VectorStore vectorStore;
    private final Bm25SparseVectorizer sparseVectorizer;
    private final boolean queryRewriteEnabled;
    private final boolean rerankEnabled;
    private final int maxRewrites;
    private final int denseLimit;
    private final int keywordLimit;
    private final int candidateLimit;
    private final int contextLimit;
    private final int rrfConstant;
    private final RagObservability observability;

    public RetrievalServiceImpl(LlmService llm,
                                PlatformRepository repo,
                                VectorStore vectorStore,
                                Bm25SparseVectorizer sparseVectorizer,
                                @Value("${rag.retrieval.query-rewrite-enabled:true}") boolean queryRewriteEnabled,
                                @Value("${rag.retrieval.rerank-enabled:true}") boolean rerankEnabled,
                                @Value("${rag.retrieval.max-rewrites:3}") int maxRewrites,
                                @Value("${rag.retrieval.dense-limit:50}") int denseLimit,
                                @Value("${rag.retrieval.keyword-limit:50}") int keywordLimit,
                                @Value("${rag.retrieval.candidate-limit:50}") int candidateLimit,
                                @Value("${rag.retrieval.context-limit:5}") int contextLimit,
                                @Value("${rag.retrieval.rrf-constant:60}") int rrfConstant,
                                RagObservability observability) {
        this.llm = llm;
        this.repo = repo;
        this.vectorStore = vectorStore;
        this.sparseVectorizer = sparseVectorizer;
        this.queryRewriteEnabled = queryRewriteEnabled;
        this.rerankEnabled = rerankEnabled;
        this.maxRewrites = Math.max(0, Math.min(maxRewrites, 5));
        this.denseLimit = Math.max(10, Math.min(denseLimit, 100));
        this.keywordLimit = Math.max(10, Math.min(keywordLimit, 100));
        this.candidateLimit = Math.max(5, Math.min(candidateLimit, 100));
        this.contextLimit = Math.max(1, Math.min(contextLimit, 12));
        this.rrfConstant = Math.max(1, Math.min(rrfConstant, 200));
        this.observability = observability;
    }

    @Override
    public RetrievalResult retrieve(String question, List<Long> permittedKbIds) {
        long started = System.nanoTime();
        List<String> queries = queryVariants(question);
        Map<CandidateKey, Candidate> candidates = new LinkedHashMap<>();
        Map<Long, Map<String, Object>> parentCache = new HashMap<>();
        int denseCount = 0;
        int keywordCount = 0;
        int hybridFallbackCount = 0;
        String traceId = UUID.randomUUID().toString();
        for (String query : queries) {
            List<Float> denseVector = llm.embed(query);
            SortedMap<Long, Float> sparseVector = sparseVectorizer.vectorize(query);
            List<Map<String, Object>> dense;
            try {
                dense = vectorStore.searchAdaptive(denseVector, denseLimit, permittedKbIds);
            } catch (RuntimeException denseFailure) {
                dense = List.of();
                hybridFallbackCount++;
            }
            List<Map<String, Object>> sparse;
            try {
                sparse = vectorStore.sparseSearch(sparseVector, keywordLimit, permittedKbIds);
            } catch (RuntimeException sparseFailure) {
                sparse = List.of();
                hybridFallbackCount++;
            }
            denseCount += dense.size();
            keywordCount += sparse.size();
            mergeRanked(candidates, dense, "dense", parentCache);
            mergeRanked(candidates, sparse, "sparse", parentCache);
        }
        List<Candidate> fused = candidates.values().stream()
                .sorted(Comparator.comparingDouble(Candidate::rrfScore).reversed())
                .limit(candidateLimit).toList();
        long rerankStarted = System.nanoTime();
        boolean reranked = rerank(question, fused);
        observability.record("llm.rerank", System.nanoTime() - rerankStarted, reranked || !rerankEnabled || fused.isEmpty(),
                Map.of("candidate_count", fused.size(), "enabled", rerankEnabled, "applied", reranked, "trace_id", traceId));
        Comparator<Candidate> order = reranked
                ? Comparator.comparingDouble(Candidate::rerankScore).reversed()
                        .thenComparing(Comparator.comparingDouble(Candidate::rrfScore).reversed())
                : Comparator.comparingDouble(Candidate::rrfScore).reversed();
        List<Map<String, Object>> hits = fused.stream().sorted(order).limit(contextLimit).map(Candidate::toHit).toList();
        Map<String, Object> trace = new LinkedHashMap<>();
        trace.put("trace_id", traceId);
        trace.put("queryCount", queries.size());
        trace.put("denseCandidateCount", denseCount);
        trace.put("keywordCandidateCount", keywordCount);
        trace.put("hybridFallbackCount", hybridFallbackCount);
        trace.put("fusedCandidateCount", fused.size());
        trace.put("parentCandidateCount", fused.size());
        trace.put("reranked", reranked);
        trace.put("contextChunkCount", hits.size());
        observability.record("retrieval.hybrid_rrf", System.nanoTime() - started, true, trace);
        return new RetrievalResult(hits, trace);
    }

    private List<String> queryVariants(String question) {
        Set<String> values = new LinkedHashSet<>();
        addQuery(values, question);
        if (queryRewriteEnabled) {
            for (String rewritten : llm.rewriteQueries(question, maxRewrites)) addQuery(values, rewritten);
        }
        return List.copyOf(values);
    }

    private void addQuery(Set<String> values, String query) {
        if (query == null) return;
        String normalized = query.replaceAll("\\s+", " ").trim();
        if (!normalized.isBlank() && normalized.length() <= 300) values.add(normalized);
    }

    private void mergeRanked(Map<CandidateKey, Candidate> candidates, List<Map<String, Object>> hits, String source,
                             Map<Long, Map<String, Object>> parentCache) {
        for (int index = 0; index < hits.size(); index++) {
            Map<String, Object> hit = hits.get(index);
            Map<String, Object> payload = payload(hit.get("payload"));
            String level = String.valueOf(payload.getOrDefault("chunk_level", "PARENT"));
            long chunkId = number(payload.get("chunk_id"), number(hit.get("id"), 0L));
            String content = text(payload, "content", "text", "snippet");
            if (chunkId <= 0 || content.isBlank()) continue;

            long docId = number(payload.get("doc_id"), number(payload.get("docId"), 0L));
            long kbId = number(payload.get("kb_id"), number(payload.get("kbId"), 0L));
            long parentId = number(payload.get("parent_id"), number(payload.get("parentId"), 0L));
            boolean child = "CHILD".equalsIgnoreCase(level)
                    || (parentId > 0 && parentId != chunkId && !"PARENT".equalsIgnoreCase(level));
            Map<String, Object> parent = child ? resolveParent(parentId, payload, parentCache) : null;
            if (child && parent == null) continue;
            boolean parentPoint = !child && parentId > 0 && parentId == chunkId;
            CandidateKey candidateKey = child && parentId > 0
                    ? new CandidateKey(docId, kbId, parentId, true)
                    : new CandidateKey(docId, kbId, chunkId, parentPoint);
            String parentContent = child ? text(parent, "content", "text") : content;
            if (parentContent.isBlank()) continue;
            Candidate candidate = candidates.computeIfAbsent(candidateKey,
                    ignored -> new Candidate(hit, payload, parentContent, child));
            candidate.consider(hit, payload, parentContent, child);
            candidate.add(source, 1D / (rrfConstant + index + 1));
        }
    }

    private Map<String, Object> resolveParent(long parentId, Map<String, Object> childPayload,
                                              Map<Long, Map<String, Object>> parentCache) {
        if (parentId <= 0) return null;
        if (parentCache.containsKey(parentId)) {
            Map<String, Object> cached = parentCache.get(parentId);
            return parentBelongsToChild(cached, childPayload) ? cached : null;
        }
        Map<String, Object> parent;
        try {
            parent = repo.parentChunk(parentId);
        } catch (RuntimeException ignored) {
            parent = null;
        }
        boolean belongs = parentBelongsToChild(parent, childPayload);
        Map<String, Object> result = belongs ? parent : null;
        parentCache.put(parentId, result);
        return result;
    }

    private boolean parentBelongsToChild(Map<String, Object> parent, Map<String, Object> childPayload) {
        long childDocId = number(childPayload.get("doc_id"), number(childPayload.get("docId"), 0L));
        long childKbId = number(childPayload.get("kb_id"), number(childPayload.get("kbId"), 0L));
        return parent != null
                && number(parent.get("docId"), number(parent.get("doc_id"), 0L)) == childDocId
                && number(parent.get("kbId"), number(parent.get("kb_id"), 0L)) == childKbId
                && !text(parent, "content", "text").isBlank();
    }

    private boolean rerank(String question, List<Candidate> candidates) {
        if (!rerankEnabled || candidates.isEmpty()) return false;
        try {
            List<String> documents = candidates.stream().map(Candidate::contentForRerank).toList();
            List<LlmService.RerankResult> results = llm.rerank(question, documents, contextLimit);
            if (results.isEmpty()) return false;
            boolean applied = false;
            for (LlmService.RerankResult result : results) {
                if (result.index() >= 0 && result.index() < candidates.size()
                        && Double.isFinite(result.score())) {
                    candidates.get(result.index()).setRerankScore(result.score());
                    applied = true;
                }
            }
            return applied;
        } catch (Exception ignored) {
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> payload(Object raw) {
        return raw instanceof Map<?, ?> values ? (Map<String, Object>) values : Map.of();
    }

    private String text(Map<String, Object> values, String... keys) {
        if (values == null) return "";
        for (String key : keys) {
            Object value = values.get(key);
            if (value != null && !String.valueOf(value).isBlank()) return String.valueOf(value).trim();
        }
        return "";
    }

    private long number(Object value, long fallback) {
        if (value instanceof Number number) return number.longValue();
        try {
            return value == null ? fallback : Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static final class Candidate {
        private final Map<String, Object> hit = new LinkedHashMap<>();
        private final Map<String, Object> payload = new LinkedHashMap<>();
        private final Set<String> sources = new LinkedHashSet<>();
        private String parentContent;
        private boolean childRepresentative;
        private double rrfScore;
        private Double rerankScore;

        private Candidate(Map<String, Object> hit, Map<String, Object> payload, String parentContent,
                          boolean childRepresentative) {
            this.parentContent = parentContent;
            this.childRepresentative = childRepresentative;
            consider(hit, payload, parentContent, childRepresentative);
        }

        private void consider(Map<String, Object> nextHit, Map<String, Object> nextPayload,
                              String nextParentContent, boolean nextIsChild) {
            if (!hit.isEmpty() && (!nextIsChild || childRepresentative)) return;
            hit.clear();
            hit.putAll(nextHit);
            payload.clear();
            payload.putAll(nextPayload);
            parentContent = nextParentContent;
            childRepresentative = nextIsChild;
        }

        private void add(String source, double score) {
            sources.add(source);
            rrfScore += score;
        }

        private double rrfScore() {
            return rrfScore;
        }

        private double rerankScore() {
            return rerankScore == null ? Double.NEGATIVE_INFINITY : rerankScore;
        }

        private void setRerankScore(double score) {
            rerankScore = score;
        }

        private String contentForRerank() {
            String content = parentContent == null ? "" : parentContent;
            return content.length() <= 2_000 ? content : content.substring(0, 2_000);
        }

        private Map<String, Object> toHit() {
            Map<String, Object> result = new LinkedHashMap<>(hit);
            result.put("payload", payload);
            result.put("score", rerankScore == null ? rrfScore : rerankScore);
            result.put("retrievalSources", List.copyOf(sources));
            result.put("rrfScore", rrfScore);
            if (rerankScore != null) result.put("rerankScore", rerankScore);
            return result;
        }
    }

    private record CandidateKey(long docId, long kbId, long id, boolean parentScoped) {
    }
}
