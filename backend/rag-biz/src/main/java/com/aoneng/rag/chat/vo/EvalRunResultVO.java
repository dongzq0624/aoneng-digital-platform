package com.aoneng.rag.chat.vo;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 单条检索评测结果响应体。
 *
 * @param caseId           评测用例 ID
 * @param question         评测查询
 * @param expectedChunkIds 期望命中的分块 ID
 * @param returnedChunkIds 实际命中的分块 ID
 * @param recallAtK        召回率
 * @param mrrAtK           MRR
 * @param nDcgAtK          nDCG
 * @param hit              是否命中
 * @param retrieval        检索 trace 详情
 */
public record EvalRunResultVO(
        long caseId,
        String question,
        Set<Long> expectedChunkIds,
        Set<Long> returnedChunkIds,
        Double recallAtK,
        Double mrrAtK,
        Double nDcgAtK,
        boolean hit,
        Map<String, Object> retrieval) {
}
