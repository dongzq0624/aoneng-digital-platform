package com.example.rag.kb.convert;

import com.example.rag.doc.vo.KnowledgeBaseDocumentVO;
import com.example.rag.doc.vo.KnowledgeBaseVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * 知识库相关转换器。在 {@code PlatformRepository} 返回的 {@code Map<String, Object>} 与
 * 控制器使用的强类型 VO 之间架桥，统一处理 Number / 时间戳 / 嵌套列表等松散类型的容错。
 */
@Mapper
public interface KbConvert {

    /** 单例 Mapper 实例，业务侧可直接 {@code KbConvert.INSTANCE.xxx()} 调用。 */
    KbConvert INSTANCE = Mappers.getMapper(KbConvert.class);

    /**
     * 知识库行 Map → {@link KnowledgeBaseVO}。
     * 字段名变更时需同步前端类型定义。
     */
    @Mapping(target = "id", source = "id", qualifiedByName = "longFromObject")
    @Mapping(target = "name", source = "name", qualifiedByName = "stringFromObject")
    @Mapping(target = "description", source = "description", qualifiedByName = "stringFromObject")
    @Mapping(target = "category", source = "category", qualifiedByName = "stringFromObject")
    @Mapping(target = "visibility", source = "visibility", qualifiedByName = "stringFromObject")
    @Mapping(target = "ownerId", source = "ownerId", qualifiedByName = "longFromObject")
    @Mapping(target = "deptId", source = "deptId", qualifiedByName = "longOrNullFromObject")
    @Mapping(target = "chunkSize", source = "chunkSize", qualifiedByName = "intOrNullFromObject")
    @Mapping(target = "chunkOverlap", source = "chunkOverlap", qualifiedByName = "intOrNullFromObject")
    @Mapping(target = "docCount", source = "docCount", qualifiedByName = "intOrNullFromObject")
    @Mapping(target = "allowedDeptIds", source = "allowedDeptIds", qualifiedByName = "longListFromObject")
    @Mapping(target = "canManage", source = "canManage", qualifiedByName = "boolFromObject")
    @Mapping(target = "canConfigureDepartments", source = "canConfigureDepartments", qualifiedByName = "boolFromObject")
    @Mapping(target = "createdAt", source = "createdAt", qualifiedByName = "instantFromObject")
    @Mapping(target = "updatedAt", source = "updatedAt", qualifiedByName = "instantFromObject")
    KnowledgeBaseVO toBaseResponse(Map<String, Object> row);

    /** 批量转换知识库行。 */
    List<KnowledgeBaseVO> toBaseResponses(List<Map<String, Object>> rows);

    /** 单条文档行 Map → {@link KnowledgeBaseDocumentVO}。 */
    @Mapping(target = "id", source = "id", qualifiedByName = "longFromObject")
    @Mapping(target = "kbId", source = "kbId", qualifiedByName = "longFromObject")
    @Mapping(target = "fileName", source = "fileName", qualifiedByName = "stringFromObject")
    @Mapping(target = "fileType", source = "fileType", qualifiedByName = "stringFromObject")
    @Mapping(target = "fileSize", source = "fileSize", qualifiedByName = "longFromObject")
    @Mapping(target = "version", source = "version", qualifiedByName = "intOrNullFromObject")
    @Mapping(target = "parseStatus", source = "parseStatus", qualifiedByName = "stringFromObject")
    @Mapping(target = "chunkStatus", source = "chunkStatus", qualifiedByName = "stringFromObject")
    @Mapping(target = "chunkCount", source = "chunkCount", qualifiedByName = "intOrNullFromObject")
    @Mapping(target = "errorMsg", source = "errorMsg", qualifiedByName = "stringFromObject")
    @Mapping(target = "createdAt", source = "createdAt", qualifiedByName = "instantFromObject")
    @Mapping(target = "updatedAt", source = "updatedAt", qualifiedByName = "instantFromObject")
    KnowledgeBaseDocumentVO toDocumentResponse(Map<String, Object> row);

    /** 批量转换文档行。 */
    List<KnowledgeBaseDocumentVO> toDocumentResponses(List<Map<String, Object>> rows);

    @org.mapstruct.Named("stringFromObject")
    default String stringFromObject(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    @org.mapstruct.Named("longFromObject")
    default long longFromObject(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }

    @org.mapstruct.Named("longOrNullFromObject")
    default Long longOrNullFromObject(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }

    @org.mapstruct.Named("intOrNullFromObject")
    default Integer intOrNullFromObject(Object value) {
        if (value == null) return null;
        if (value instanceof Number number) return number.intValue();
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    @org.mapstruct.Named("longListFromObject")
    default List<Long> longListFromObject(Object value) {
        if (!(value instanceof List<?> list)) return List.of();
        return list.stream().filter(Number.class::isInstance).map(Number.class::cast)
                .map(Number::longValue).distinct().toList();
    }

    @org.mapstruct.Named("boolFromObject")
    default boolean boolFromObject(Object value) {
        if (value instanceof Boolean bool) return bool;
        if (value instanceof Number number) return number.intValue() != 0;
        return false;
    }

    @org.mapstruct.Named("instantFromObject")
    default Instant instantFromObject(Object value) {
        if (value == null) return null;
        if (value instanceof Instant instant) return instant;
        if (value instanceof OffsetDateTime odt) return odt.toInstant();
        if (value instanceof java.sql.Timestamp ts) return ts.toInstant();
        return null;
    }
}
