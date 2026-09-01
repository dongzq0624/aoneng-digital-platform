package com.example.rag.service;

import com.example.rag.chat.service.QdrantService;
import com.example.rag.chat.service.RagRetrievalService;
import com.example.rag.service.DashScopeService;
import com.example.rag.service.PlatformRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 检索流水线服务默认实现。
 * 混合密集向量 + 关键词检索，使用 RRF 融合，
 * 可选地对接 DashScope 重排模型对候选进行精排。
 *
 * <p>配置参数（均位于 {@code rag.retrieval.*}）：</p>
 * <ul>
 *   <li>{@code query-rewrite-enabled}: 是否启用 DashScope 查询改写</li>
 *   <li>{@code rerank-enabled}: 是否对候选池应用 DashScope 重排</li>
 *   <li>{@code max-rewrites}, {@code dense-limit}, {@code keyword-limit},
 *       {@code candidate-limit}, {@code context-limit}, {@code rrf-constant}: 调优参数</li>
 * </ul>
 */
@Service
public class RagRetrievalServiceImpl implements RagRetrievalService {

    private final DashScopeService dash;
    private final QdrantService qdrant;
    private final PlatformRepository repo;
    private final boolean queryRewriteEnabled;
    private final boolean rerankEnabled;
    private final int maxRewrites;
    private final int denseLimit;
    private final int keywordLimit;
    private final int candidateLimit;
    private final int contextLimit;
    private final int rrfConstant;

    public RagRetrievalServiceImpl(DashScopeService dash,
                                   QdrantService qdrant,
                                   PlatformRepository repo,
                                   @Value("${rag.retrieval.query-rewrite-enabled:true}") boolean queryRewriteEnabled,
                                   @Value("${rag.retrieval.rerank-enabled:true}") boolean rerankEnabled,
                                   @Value("${rag.retrieval.max-rewrites:3}") int maxRewrites,
                                   @Value("${rag.retrieval.dense-limit:40}") int denseLimit,
                                   @Value("${rag.retrieval.keyword-limit:40}") int keywordLimit,
                                   @Value("${rag.retrieval.candidate-limit:40}") int candidateLimit,
                                   @Value("${rag.retrieval.context-limit:8}") int contextLimit,
                                   @Value("${rag.retrieval.rrf-constant:60}") int rrfConstant) {
        this.dash = dash;
        this.qdrant = qdrant;
        this.repo = repo;
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
        for (String query : queries) {
            List<Map<String, Object>> dense = qdrant.searchAdaptive(dash.embed(query), denseLimit, permittedKbIds);
            denseCount += dense.size();
            mergeRanked(candidates, dense, "dense");
            List<Map<String, Object>> keywords = repo.keywordChunks(keywordTerms(query), permittedKbIds, keywordLimit);
            keywordCount += keywords.size();
            mergeRanked(candidates, keywords, "keyword");
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
        trace.put("fusedCandidateCount", fused.size());
        trace.put("reranked", reranked);
        trace.put("contextChunkCount", hits.size());
        return new RetrievalResult(hits, trace);
    }

    private List<String> queryVariants(String question) {
        Set<String> values = new LinkedHashSet<>();
        addQuery(values, question);
        if (queryRewriteEnabled) for (String rewritten : dash.rewriteQueries(question, maxRewrites)) addQuery(values, rewritten);
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
            List<DashScopeService.RerankResult> results = dash.rerank(question, documents, contextLimit);
            if (results.isEmpty()) return false;
            for (DashScopeService.RerankResult result : results) candidates.get(result.index()).setRerankScore(result.score());
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private List<String> keywordTerms(String query) {
        String compact = query == null ? "" : query.replaceAll("[^\\p{IsHan}A-Za-z0-9_\\-]", "").trim();
        LinkedHashSet<String> terms = new LinkedHashSet<>();
        if (compact.length() >= 2) terms.add(compact.substring(0, Math.min(compact.length(), 60)));
        for (String token : (query == null ? "" : query).split("[^\\p{IsHan}A-Za-z0-9_\\-]+")) {
            if (token.length() >= 2) terms.add(token.substring(0, Math.min(token.length(), 40)));
            if (terms.size() >= 6) break;
        }
        if (terms.size() < 6 && compact.length() > 2) {
            for (int index = 0; index + 1 < compact.length() && terms.size() < 6; index++) {
                String pair = compact.substring(index, index + 2);
                if (pair.codePoints().allMatch(Character::isIdeographic)) terms.add(pair);
            }
        }
        if (terms.isEmpty() && !compact.isBlank()) terms.add(compact);
        return new ArrayList<>(terms).subList(0, Math.min(terms.size(), 6));
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
