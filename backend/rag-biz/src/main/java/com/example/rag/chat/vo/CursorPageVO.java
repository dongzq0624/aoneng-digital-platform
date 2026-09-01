package com.example.rag.chat.vo;

import java.util.List;

/**
 * 游标分页响应包装体。复用于会话列表、消息历史列表等游标式分页接口。
 *
 * @param items      当前页条目
 * @param nextCursor 下一页游标（可空）
 * @param hasMore    是否还有下一页
 */
public record CursorPageVO<T>(List<T> items, String nextCursor, boolean hasMore) {
}
