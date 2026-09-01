package com.example.rag.chat.service;

import com.example.rag.domain.KbScope;
import com.example.rag.chat.vo.EvalRunResultVO;

/**
 * 检索评测服务接口。计算评测用例的召回率、MRR 和 nDCG 等指标。
 */
public interface RagEvalService {

    /**
     * 执行检索评测。
     *
     * @param caseId 评测用例 ID
     * @param scope  用户权限范围
     * @return 评测结果（召回率、MRR、nDCG 等指标）
     */
    EvalRunResultVO evaluate(long caseId, KbScope scope);
}
