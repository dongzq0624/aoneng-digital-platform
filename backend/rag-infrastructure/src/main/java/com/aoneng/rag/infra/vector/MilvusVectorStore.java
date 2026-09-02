package com.aoneng.rag.infra.vector;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.protobuf.ByteString;
import com.aoneng.rag.infra.config.MilvusProperties;
import io.milvus.client.MilvusServiceClient;
import io.milvus.grpc.DataType;
import io.milvus.grpc.SearchResultData;
import io.milvus.param.ConnectParam;
import io.milvus.param.IndexType;
import io.milvus.param.MetricType;
import io.milvus.param.R;
import io.milvus.param.collection.CreateCollectionParam;
import io.milvus.param.collection.FieldType;
import io.milvus.param.collection.HasCollectionParam;
import io.milvus.param.collection.LoadCollectionParam;
import io.milvus.param.dml.DeleteParam;
import io.milvus.param.dml.AnnSearchParam;
import io.milvus.param.dml.HybridSearchParam;
import io.milvus.param.dml.SearchParam;
import io.milvus.param.dml.UpsertParam;
import io.milvus.param.index.CreateIndexParam;
import io.milvus.param.dml.ranker.RRFRanker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import jakarta.annotation.PreDestroy;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;

/** Milvus-backed implementation of the application-neutral vector store contract. */
@Component
public class MilvusVectorStore implements VectorStore {

    private static final Logger log = LoggerFactory.getLogger(MilvusVectorStore.class);
    private static final String VECTOR_FIELD = "embedding";
    private static final String SPARSE_VECTOR_FIELD = "sparse_embedding";
    private static final String PAYLOAD_FIELD = "payload";

    private final MilvusProperties properties;
    private final MilvusServiceClient client;
    private final Gson gson = new Gson();

    public MilvusVectorStore(MilvusProperties properties) {
        this.properties = properties;
        ConnectParam.Builder builder = ConnectParam.newBuilder()
                .withHost(properties.host())
                .withPort(properties.port())
                .withConnectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                .withRpcDeadline(10, java.util.concurrent.TimeUnit.SECONDS);
        if (!properties.token().isBlank()) builder.withToken(properties.token());
        this.client = new MilvusServiceClient(builder.build());
    }

    @EventListener(ApplicationReadyEvent.class)
    public void initializeCollection() {
        try {
            ensureCollection();
            R<?> loaded = client.loadCollection(LoadCollectionParam.newBuilder()
                    .withCollectionName(properties.collection()).withSyncLoad(false).build());
            if (isFailure(loaded)) throw failure("加载 Milvus 集合失败", loaded);
            log.info("Milvus collection initialized: {}", properties.collection());
        } catch (Exception e) {
            log.error("Milvus collection initialization failed, collection={}", properties.collection(), e);
        }
    }

    @PreDestroy
    public void close() {
        try {
            client.close(5_000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("关闭 Milvus 客户端时被中断");
        }
    }

    private void ensureCollection() {
        R<Boolean> existing = client.hasCollection(HasCollectionParam.newBuilder()
                .withCollectionName(properties.collection()).build());
        if (isFailure(existing)) throw failure("检查 Milvus 集合失败", existing);
        if (Boolean.TRUE.equals(existing.getData())) return;

        List<FieldType> fields = List.of(
                FieldType.newBuilder().withName("id").withDataType(DataType.Int64)
                        .withPrimaryKey(true).withAutoID(false).build(),
                FieldType.newBuilder().withName("kb_id").withDataType(DataType.Int64).build(),
                FieldType.newBuilder().withName("doc_id").withDataType(DataType.Int64).build(),
                FieldType.newBuilder().withName(VECTOR_FIELD).withDataType(DataType.FloatVector)
                        .withDimension(properties.dimension()).build(),
                FieldType.newBuilder().withName(SPARSE_VECTOR_FIELD).withDataType(DataType.SparseFloatVector).build(),
                FieldType.newBuilder().withName(PAYLOAD_FIELD).withDataType(DataType.JSON).build());
        R<?> created = client.createCollection(CreateCollectionParam.newBuilder()
                .withCollectionName(properties.collection()).withFieldTypes(fields).withShardsNum(2).build());
        if (isFailure(created)) throw failure("创建 Milvus 集合失败", created);
        R<?> index = client.createIndex(CreateIndexParam.newBuilder()
                .withCollectionName(properties.collection()).withFieldName(VECTOR_FIELD)
                .withIndexType(IndexType.AUTOINDEX).withMetricType(MetricType.COSINE).build());
        if (isFailure(index)) throw failure("创建 Milvus 向量索引失败", index);
        R<?> sparseIndex = client.createIndex(CreateIndexParam.newBuilder()
                .withCollectionName(properties.collection()).withFieldName(SPARSE_VECTOR_FIELD)
                .withIndexType(IndexType.SPARSE_INVERTED_INDEX).withMetricType(MetricType.IP).build());
        if (isFailure(sparseIndex)) throw failure("sparse index creation failed", sparseIndex);
    }

    @Override
    public void upsert(long id, List<Float> vector, Map<String, Object> payload) {
        upsert(id, vector, new java.util.TreeMap<>(), payload);
    }

    @Override
    public void upsert(long id, List<Float> vector, SortedMap<Long, Float> sparseVector,
                       Map<String, Object> payload) {
        requireVector(vector);
        JsonObject row = gson.toJsonTree(payload == null ? Map.of() : payload).getAsJsonObject();
        row.addProperty("id", id);
        row.addProperty("kb_id", number(payload == null ? null : payload.get("kb_id")));
        row.addProperty("doc_id", number(payload == null ? null : payload.get("doc_id")));
        row.add(VECTOR_FIELD, gson.toJsonTree(vector));
        JsonObject sparse = new JsonObject();
        if (sparseVector != null) sparseVector.forEach((index, value) -> sparse.addProperty(String.valueOf(index), value));
        row.add(SPARSE_VECTOR_FIELD, sparse);
        JsonObject entity = new JsonObject();
        entity.add("payload", gson.toJsonTree(payload == null ? Map.of() : payload));
        row.add(PAYLOAD_FIELD, entity.get("payload"));
        R<?> result = client.upsert(UpsertParam.newBuilder().withCollectionName(properties.collection())
                .withRows(List.of(row)).build());
        if (isFailure(result)) throw failure("Milvus 向量写入失败", result);
    }

    @Override
    public void deleteByDocument(long docId) {
        R<?> result = client.delete(DeleteParam.newBuilder().withCollectionName(properties.collection())
                .withExpr("doc_id == " + docId).build());
        if (isFailure(result)) throw failure("Milvus 文档向量删除失败", result);
    }

    @Override
    public List<Map<String, Object>> search(List<Float> vector, int limit) {
        return search(vector, limit, List.of());
    }

    @Override
    public List<Map<String, Object>> search(List<Float> vector, int limit, List<Long> allowedKbIds) {
        return searchInternal(vector, limit, allowedKbIds, properties.scoreThreshold());
    }

    @Override
    public List<Map<String, Object>> searchAdaptive(List<Float> vector, int limit, List<Long> allowedKbIds) {
        List<Map<String, Object>> result = search(vector, limit, allowedKbIds);
        if (!result.isEmpty() || properties.scoreThreshold() <= 0.05D) return result;
        return searchInternal(vector, limit, allowedKbIds, Math.max(0.05D, properties.scoreThreshold() * 0.5D));
    }

    @Override
    public List<Map<String, Object>> sparseSearch(SortedMap<Long, Float> sparseVector, int limit,
                                                  List<Long> allowedKbIds) {
        if (sparseVector == null || sparseVector.isEmpty()
                || allowedKbIds != null && allowedKbIds.isEmpty()) return List.of();
        String expr = allowedKbIds == null || allowedKbIds.isEmpty() ? null
                : "kb_id in [" + allowedKbIds.stream().map(String::valueOf)
                .collect(java.util.stream.Collectors.joining(",")) + "]";
        SearchParam.Builder builder = SearchParam.newBuilder().withCollectionName(properties.collection())
                .withVectorFieldName(SPARSE_VECTOR_FIELD).withSparseFloatVectors(List.of(sparseVector))
                .withTopK(Math.max(1, Math.min(limit, 16384))).withMetricType(MetricType.IP)
                .withOutFields(List.of(PAYLOAD_FIELD, "kb_id", "doc_id"));
        if (expr != null) builder.withExpr(expr);
        R<io.milvus.grpc.SearchResults> response = client.search(builder.build());
        if (isFailure(response)) throw failure("Milvus sparse search failed", response);
        return toHits(response.getData(), 0D);
    }

    @Override
    public List<Map<String, Object>> hybridSearch(List<Float> denseVector, SortedMap<Long, Float> sparseVector,
                                                  int limit, List<Long> allowedKbIds) {
        requireVector(denseVector);
        if (sparseVector == null || sparseVector.isEmpty()) return searchAdaptive(denseVector, limit, allowedKbIds);
        if (allowedKbIds != null && allowedKbIds.isEmpty()) return List.of();
        String expr = allowedKbIds == null || allowedKbIds.isEmpty() ? null
                : "kb_id in [" + allowedKbIds.stream().map(String::valueOf)
                .collect(java.util.stream.Collectors.joining(",")) + "]";
        int topK = Math.max(1, Math.min(limit, 16384));
        AnnSearchParam dense = AnnSearchParam.newBuilder().withVectorFieldName(VECTOR_FIELD)
                .withMetricType(MetricType.COSINE).withTopK(topK)
                .withFloatVectors(List.of(denseVector)).withExpr(expr).build();
        AnnSearchParam sparse = AnnSearchParam.newBuilder().withVectorFieldName(SPARSE_VECTOR_FIELD)
                .withMetricType(MetricType.IP).withTopK(topK)
                .withSparseFloatVectors(List.of(sparseVector)).withExpr(expr).build();
        R<io.milvus.grpc.SearchResults> response = client.hybridSearch(HybridSearchParam.newBuilder()
                .withCollectionName(properties.collection()).addSearchRequest(dense).addSearchRequest(sparse)
                .withRanker(RRFRanker.newBuilder().withK(60).build()).withTopK(topK)
                .withOutFields(List.of(PAYLOAD_FIELD, "kb_id", "doc_id")).build());
        if (isFailure(response)) throw failure("Milvus hybrid search failed", response);
        return toHits(response.getData(), 0D);
    }

    private List<Map<String, Object>> searchInternal(List<Float> vector, int limit, List<Long> allowedKbIds,
                                                     double threshold) {
        requireVector(vector);
        if (allowedKbIds != null && allowedKbIds.isEmpty()) return List.of();
        String expr = null;
        if (allowedKbIds != null && !allowedKbIds.isEmpty()) {
            expr = "kb_id in [" + allowedKbIds.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(",")) + "]";
        }
        SearchParam.Builder builder = SearchParam.newBuilder().withCollectionName(properties.collection())
                .withVectorFieldName(VECTOR_FIELD).withFloatVectors(List.of(vector))
                .withTopK(Math.max(1, Math.min(limit, 16384))).withMetricType(MetricType.COSINE)
                .withOutFields(List.of(PAYLOAD_FIELD, "kb_id", "doc_id"));
        if (expr != null) builder.withExpr(expr);
        R<io.milvus.grpc.SearchResults> response = client.search(builder.build());
        if (isFailure(response)) throw failure("Milvus 向量检索失败", response);
        return toHits(response.getData(), threshold);
    }

    private List<Map<String, Object>> toHits(io.milvus.grpc.SearchResults response, double threshold) {
        if (response == null || !response.hasResults()) return List.of();
        SearchResultData data = response.getResults();
        List<Long> ids = data.hasIds() ? data.getIds().getIntId().getDataList() : List.of();
        List<Map<String, Object>> hits = new ArrayList<>();
        for (int i = 0; i < data.getScoresCount() && i < ids.size(); i++) {
            double score = data.getScores(i);
            if (!Double.isFinite(score) || score < threshold) continue;
            Map<String, Object> hit = new java.util.LinkedHashMap<>();
            hit.put("id", ids.get(i));
            hit.put("score", score);
            hit.put("payload", payloadAt(data, i));
            hits.add(hit);
        }
        return hits;
    }

    private Map<String, Object> payloadAt(SearchResultData data, int index) {
        for (io.milvus.grpc.FieldData field : data.getFieldsDataList()) {
            if (!PAYLOAD_FIELD.equals(field.getFieldName()) || !field.hasScalars()
                    || !field.getScalars().hasJsonData() || index >= field.getScalars().getJsonData().getDataCount()) continue;
            ByteString json = field.getScalars().getJsonData().getData(index);
            try {
                return gson.fromJson(json.toString(StandardCharsets.UTF_8), Map.class);
            } catch (RuntimeException ignored) {
                return Map.of();
            }
        }
        return Map.of();
    }

    private void requireVector(List<Float> vector) {
        if (vector == null || vector.size() != properties.dimension()) {
            throw new IllegalArgumentException("嵌入向量维度与 Milvus 集合配置不一致");
        }
    }

    private long number(Object value) {
        return value instanceof Number n ? n.longValue() : 0L;
    }

    private boolean isFailure(R<?> result) {
        return result == null || result.getStatus() == null || result.getStatus() != R.Status.Success.getCode();
    }

    private IllegalStateException failure(String message, R<?> result) {
        return new IllegalStateException(message + ": " + (result == null ? "无响应" : result.getMessage()),
                result == null ? null : result.getException());
    }
}
