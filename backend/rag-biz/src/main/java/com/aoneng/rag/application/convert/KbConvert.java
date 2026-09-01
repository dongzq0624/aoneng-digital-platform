package com.aoneng.rag.application.convert;

import com.aoneng.rag.doc.vo.AllowedDepartmentsVO;
import com.aoneng.rag.doc.vo.KnowledgeBaseDocumentVO;
import com.aoneng.rag.doc.vo.KnowledgeBaseVO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * 知识库 DTO/VO 与持久化层 {@code Map<String, Object>} 之间的转换器。
 */
@Mapper
public interface KbConvert {

    KbConvert INSTANCE = Mappers.getMapper(KbConvert.class);

    default List<KnowledgeBaseVO> toBaseResponses(List<Map<String, Object>> rows) {
        if (rows == null) return List.of();
        return rows.stream().map(this::toBaseResponse).toList();
    }

    default KnowledgeBaseVO toBaseResponse(Map<String, Object> row) {
        if (row == null) return null;
        return new KnowledgeBaseVO(
                longOrZero(row.get("id")),
                stringOrNull(row.get("name")),
                stringOrNull(row.get("description")),
                stringOrNull(row.get("category")),
                stringOrNull(row.get("visibility")),
                longOrZero(row.get("ownerId")),
                longOrNull(row.get("deptId")),
                intOrNull(row.get("chunkSize")),
                intOrNull(row.get("chunkOverlap")),
                intOrNull(row.get("docCount")),
                longList(row.get("allowedDeptIds")),
                Boolean.TRUE.equals(row.get("canManage")),
                Boolean.TRUE.equals(row.get("canConfigureDepartments")),
                toInstant(row.get("createdAt")),
                toInstant(row.get("updatedAt")));
    }

    default List<KnowledgeBaseDocumentVO> toDocumentResponses(List<Map<String, Object>> rows) {
        if (rows == null) return List.of();
        return rows.stream().map(this::toDocumentResponse).toList();
    }

    default KnowledgeBaseDocumentVO toDocumentResponse(Map<String, Object> row) {
        if (row == null) return null;
        return new KnowledgeBaseDocumentVO(
                longOrZero(row.get("id")),
                longOrZero(row.get("kbId")),
                stringOrNull(row.get("fileName")),
                stringOrNull(row.get("fileType")),
                longOrZero(row.get("fileSize")),
                intOrNull(row.get("version")),
                stringOrNull(row.get("parseStatus")),
                stringOrNull(row.get("chunkStatus")),
                intOrNull(row.get("chunkCount")),
                stringOrNull(row.get("errorMsg")),
                toInstant(row.get("createdAt")),
                toInstant(row.get("updatedAt")));
    }

    default AllowedDepartmentsVO toAllowedDepartmentsVO(List<Long> deptIds) {
        return new AllowedDepartmentsVO(deptIds == null ? List.of() : deptIds);
    }

    @org.mapstruct.Named("instantFromOffset")
    default Instant instantFromOffset(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }

    private static long longOrZero(Object value) {
        if (value == null) return 0L;
        if (value instanceof Number n) return n.longValue();
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    private static Long longOrNull(Object value) {
        if (value == null) return null;
        if (value instanceof Number n) return n.longValue();
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static Integer intOrNull(Object value) {
        if (value == null) return null;
        if (value instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static String stringOrNull(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static List<Long> longList(Object value) {
        if (!(value instanceof List<?> list)) return List.of();
        return list.stream()
                .filter(o -> o instanceof Number)
                .map(o -> ((Number) o).longValue())
                .distinct()
                .toList();
    }

    private static Instant toInstant(Object value) {
        if (value == null) return null;
        if (value instanceof Instant i) return i;
        if (value instanceof OffsetDateTime odt) return odt.toInstant();
        if (value instanceof java.sql.Timestamp ts) return ts.toInstant();
        try {
            return Instant.parse(String.valueOf(value));
        } catch (Exception ignored) {
            return null;
        }
    }
}
