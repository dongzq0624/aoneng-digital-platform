package com.example.rag.convert;

import com.example.rag.audit.dto.AuditLogResponse;
import com.example.rag.service.PlatformRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 审计日志转换器。{@code detail} 字段在数据库中是 JSON 文本，这里解析为 {@code Map<String, Object>}
 * 便于前端展示。解析失败时降级为 {@code {raw: 原文本}}，避免吞错。
 */
@Mapper
public interface AuditConvert {

    /** 单例 Mapper 实例。 */
    AuditConvert INSTANCE = Mappers.getMapper(AuditConvert.class);

    /**
     * 转换但不解析 detail JSON 的版本，由 {@link #toResponse(PlatformRepository.AuditLogRow, ObjectMapper)} 包装。
     */
    @Mapping(target = "id", source = "id")
    @Mapping(target = "createdAt", source = "createdAt", qualifiedByName = "instantFromOffset")
    @Mapping(target = "username", source = "username")
    @Mapping(target = "action", source = "action")
    @Mapping(target = "module", source = "module")
    @Mapping(target = "detail", ignore = true)
    @Mapping(target = "result", source = "result")
    AuditLogResponse toResponseWithoutDetail(PlatformRepository.AuditLogRow row);

    /**
     * 完整转换：解析 detail JSON。解析失败时降级为 {@code {raw: 原文本}}，日志侧不抛错。
     */
    default AuditLogResponse toResponse(PlatformRepository.AuditLogRow row, ObjectMapper mapper) {
        AuditLogResponse response = toResponseWithoutDetail(row);
        return new AuditLogResponse(response.id(), response.createdAt(), response.username(),
                response.action(), response.module(), parseDetail(row.detailJson(), mapper), response.result());
    }

    default List<AuditLogResponse> toResponses(List<PlatformRepository.AuditLogRow> rows, ObjectMapper mapper) {
        // null 入参视为空列表，保持控制器对空查询结果的稳定性。
        if (rows == null) return List.of();
        return rows.stream().map(row -> toResponse(row, mapper)).toList();
    }

    /**
     * 解析 detail JSON 为 Map。解析失败时返回 {@code {raw: 原文本}}，方便审计员定位。
     */
    default Map<String, Object> parseDetail(String json, ObjectMapper mapper) {
        if (json == null || json.isBlank()) return Collections.emptyMap();
        try {
            return mapper.readValue(json, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            return Collections.singletonMap("raw", json);
        }
    }

    @org.mapstruct.Named("instantFromOffset")
    default Instant instantFromOffset(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }
}
