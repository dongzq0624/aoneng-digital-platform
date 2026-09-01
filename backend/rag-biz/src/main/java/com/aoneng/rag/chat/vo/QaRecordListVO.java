package com.aoneng.rag.chat.vo;

import java.util.List;

/**
 * 问答记录分页响应体。
 */
public record QaRecordListVO(
        List<QaRecordListItemVO> items,
        int total,
        int page,
        int pageSize) {
}
