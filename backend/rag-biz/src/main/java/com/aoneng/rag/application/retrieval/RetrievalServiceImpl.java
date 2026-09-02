package com.aoneng.rag.application.retrieval;

import com.aoneng.rag.infra.llm.LlmService;
import com.aoneng.rag.infra.vector.Bm25SparseVectorizer;
import com.aoneng.rag.infra.vector.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;

/**
 * 检索应用服务实现。
 * 混合密集向量 + 关键词检索，使用 RRF 融合，可选地应用 DashScope 重排。
 */
@Service
public class RetrievalServiceImpl implements RetrievalService {

    private final LlmService llm;
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

    public RetrievalServiceImpl(LlmService llm,
                                VectorStore vectorStore,
                                Bm25SparseVectorizer sparseVectorizer,
                                @Value("${rag.retrieval.query-rewrite-enabled:true}") boolean queryRewriteEnabled,
                                @Value("${rag.retrieval.rerank-enabled:true}") boolean rerankEnabled,
                                @Value("${rag.retrieval.max-rewrites:3}") int maxRewrites,
                                @Value("${rag.retrieval.dense-limit:40}") int denseLimit,
                                @Value("${rag.retrieval.keyword-limit:40}") int keywordLimit,
                                @Value("${rag.retrieval.candidate-limit:40}") int candidateLimit,
                                @Value("${rag.retrieval.context-limit:8}") int contextLimit,
                                @Value("${rag.retrieval.rrf-constant:60}") int rrfConstant) {
        this.llm = llm;
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
    }

    @Override
    public RetrievalResult retrieve(String question, List<Long> permittedKbIds) {
        List<String> queries = queryVariants(question);
        Map<Long, Candidate> candidates = new LinkedHashMap<>();
        int denseCount = 0;
        int keywordCount = 0;
        int hybridFallbackCount = 0;
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
            mergeRanked(candidates, dense, "dense");
            mergeRanked(candidates, sparse, "sparse");
        }
        List<Candidate> fused = candidates.values().stream()
                .sorted(Comparator.comparingDouble(Candidate::rrfScore).reversed())
                .limit(candidateLimit).toList();
        boolean reranked = rerank(question, fused);
        Comparator<Candidate> order = reranked
                ? Comparator.comparingDouble(Candidate::rerankScore).reversed()
                        .thenComparing(Comparator.comparingDouble(Candidate::rrfScore).reversed())
                : Comparator.comparingDouble(Candidate::rrfScore).reversed();
        List<Map<String, Object>> hits = fused.stream().sorted(order).limit(contextLimit).map(Candidate::toHit).toList();
        Map<String, Object> trace = new LinkedHashMap<>();
        trace.put("queryCount", queries.size());
        trace.put("denseCandidateCount", denseCount);
        trace.put("keywordCandidateCount", keywordCount);
        trace.put("hybridFallbackCount", hybridFallbackCount);
        trace.put("fusedCandidateCount", fused.size());
        trace.put("reranked", reranked);
        trace.put("contextChunkCount", hits.size());
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

    private void mergeRanked(Map<Long, Candidate> candidates, List<Map<String, Object>> hits, String source) {
        for (int index = 0; index < hits.size(); index++) {
            Map<String, Object> hit = hits.get(index);
            Map<String, Object> payload = payload(hit.get("payload"));
            long chunkId = number(payload.get("chunk_id"), number(hit.get("id"), 0L));
            if (chunkId <= 0 || String.valueOf(payload.getOrDefault("content", "")).isBlank()) continue;
            Candidate candidate = candidates.computeIfAbsent(chunkId, ignored -> new Candidate(hit, payload));
            candidate.add(source, 1D / (rrfConstant + index + 1));
        }
    }

    private boolean rerank(String question, List<Candidate> candidates) {
        if (!rerankEnabled || candidates.isEmpty()) return false;
        try {
            List<String> documents = candidates.stream().map(Candidate::contentForRerank).toList();
            List<LlmService.RerankResult> results = llm.rerank(question, documents, contextLimit);
            if (results.isEmpty()) return false;
            for (LlmService.RerankResult result : results) candidates.get(result.index()).setRerankScore(result.score());
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> payload(Object raw) {
        return raw instanceof Map<?, ?> values ? (Map<String, Object>) values : Map.of();
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
        private final Map<String, Object> hit;
        private final Map<String, Object> payload;
        private final Set<String> sources = new LinkedHashSet<>();
        private double rrfScore;
        private Double rerankScore;

        private Candidate(Map<String, Object> hit, Map<String, Object> payload) {
            this.hit = new LinkedHashMap<>(hit);
            this.payload = new LinkedHashMap<>(payload);
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
            String content = String.valueOf(payload.getOrDefault("content", ""));
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
}
