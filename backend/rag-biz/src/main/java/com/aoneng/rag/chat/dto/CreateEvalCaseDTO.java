package com.aoneng.rag.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 创建检索评测用例请求体（仅管理员可操作）。
 *
 * @param question         评测问题（必填，最长 4000 字符）
 * @param expectedChunkIds 期望命中的分块 ID 列表（用于计算召回率、MRR、nDCG）
 * @param note             备注说明（可选）
 * @param enabled          是否启用该评测用例（可选）
 */
public record CreateEvalCaseDTO(
        @NotBlank(message = "评测问题不能为空")
        @Size(max = 4000, message = "评测问题长度不能超过 4000 个字符")
        String question,

        List<Long> expectedChunkIds,

        @Size(max = 20000, message = "参考答案长度不能超过 20000 个字符")
        String referenceAnswer,

        String note,

        Boolean enabled) {
}
