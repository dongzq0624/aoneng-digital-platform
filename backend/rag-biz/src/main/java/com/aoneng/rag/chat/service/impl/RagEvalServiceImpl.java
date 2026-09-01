package com.aoneng.rag.chat.service.impl;

import com.aoneng.rag.application.repository.KbScope;
import com.aoneng.rag.application.repository.PlatformRepository;
import com.aoneng.rag.application.retrieval.RetrievalService;
import com.aoneng.rag.chat.service.RagEvalService;
import com.aoneng.rag.chat.vo.EvalRunResultVO;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 检索评测服务默认实现。
 * 使用实际检索流水线对评测用例打分，输出召回率、MRR 和 nDCG 等指标。
 */
@Service
public class RagEvalServiceImpl implements RagEvalService {

    private final PlatformRepository repo;
    private final RetrievalService retrieval;

    public RagEvalServiceImpl(PlatformRepository repo,
                              RetrievalService retrieval) {
        this.repo = repo;
        this.retrieval = retrieval;
    }

    @Override
    public EvalRunResultVO evaluate(long caseId, KbScope scope) {
        Map<String, Object> evalCase = repo.retrievalEvalCase(caseId);
        String question = String.valueOf(evalCase.getOrDefault("question", ""));
        RetrievalService.RetrievalResult retrievalResult = retrieval.retrieve(question, repo.accessibleBaseIds(scope));
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

        int firstRelevant = -1;
        for (int index = 0; index < returnedOrdered.size(); index++) {
            if (expected.contains(returnedOrdered.get(index))) {
                firstRelevant = index + 1;
                break;
            }
        }
        double recall = expected.isEmpty() ? 0D : (double) matched.size() / expected.size();
        double mrr = firstRelevant < 0 ? 0D : 1D / firstRelevant;

        double dcg = 0D;
        for (int index = 0; index < returnedOrdered.size(); index++) {
            if (expected.contains(returnedOrdered.get(index))) dcg += 1D / (Math.log(index + 2) / Math.log(2));
        }
        double ideal = 0D;
        for (int index = 0; index < Math.min(expected.size(), returnedOrdered.size()); index++)
            ideal += 1D / (Math.log(index + 2) / Math.log(2));
        double ndcg = ideal == 0D ? 0D : dcg / ideal;

        return new EvalRunResultVO(
                caseId,
                question,
                expected,
                returned,
                recall,
                mrr,
                ndcg,
                !matched.isEmpty(),
                retrievalResult.trace());
    }

    private Set<Long> toLongSet(Object raw) {
        if (!(raw instanceof java.util.Collection<?> values)) return Set.of();
        Set<Long> result = new LinkedHashSet<>();
        for (Object value : values) {
            long id = number(value, 0L);
            if (id > 0) result.add(id);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> payload(Object raw) {
        return raw instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    private long number(Object value, long fallback) {
        Long parsed = nullableLong(value);
        return parsed == null ? fallback : parsed;
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
}
