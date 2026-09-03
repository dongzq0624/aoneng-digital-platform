package com.aoneng.rag.observability;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Lightweight, privacy-preserving tracing for the RAG pipeline. */
@Service
public class RagObservability {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(RagObservability.class);
    private final MeterRegistry meters;
    private final JdbcTemplate jdbc;
    private final RestClient langfuse;
    private final ObjectMapper mapper = new ObjectMapper();
    private final boolean enabled;
    private final String auth;
    private final String environment;
    private final ThreadLocal<String> activeTrace = new ThreadLocal<>();

    public RagObservability(MeterRegistry meters, JdbcTemplate jdbc,
                            @Value("${rag.observability.langfuse.enabled:false}") boolean enabled,
                            @Value("${rag.observability.langfuse.host:https://cloud.langfuse.com}") String host,
                            @Value("${rag.observability.langfuse.public-key:}") String publicKey,
                            @Value("${rag.observability.langfuse.secret-key:}") String secretKey,
                            @Value("${rag.observability.environment:local}") String environment) {
        this.meters = meters;
        this.jdbc = jdbc;
        this.enabled = enabled && !publicKey.isBlank() && !secretKey.isBlank();
        this.auth = Base64.getEncoder().encodeToString((publicKey + ":" + secretKey)
                .getBytes(StandardCharsets.UTF_8));
        this.environment = environment;
        this.langfuse = RestClient.builder().baseUrl(host).build();
    }

    public Span start(String operation, Map<String, Object> attributes) {
        return new Span(operation, attributes == null ? Map.of() : attributes);
    }

    public void record(String operation, long durationNanos, boolean success, Map<String, Object> attributes) {
        String normalized = operation.replaceAll("[^a-zA-Z0-9_.-]", "_");
        meters.timer("rag.pipeline.duration", "operation", normalized)
                .record(durationNanos, java.util.concurrent.TimeUnit.NANOSECONDS);
        meters.counter("rag.pipeline.calls", "operation", normalized, "outcome", success ? "success" : "failure").increment();
        Map<String, Object> eventAttributes = attributes == null ? new LinkedHashMap<>() : new LinkedHashMap<>(attributes);
        eventAttributes.putIfAbsent("trace_id", activeTrace.get() == null ? UUID.randomUUID().toString() : activeTrace.get());
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("event", "rag_pipeline");
        event.put("operation", operation);
        event.put("duration_ms", durationNanos / 1_000_000D);
        event.put("success", success);
        event.put("environment", environment);
        event.put("attributes", eventAttributes);
        try {
            log.info("{}", mapper.writeValueAsString(event));
        } catch (Exception ignored) {
            log.info("rag_pipeline operation={} durationMs={} success={}", operation,
                    durationNanos / 1_000_000D, success);
        }
        if (enabled) sendLangfuse(operation, durationNanos, success, eventAttributes);
        CompletableFuture.runAsync(() -> persist(operation, durationNanos, success, eventAttributes));
    }

    private void persist(String operation, long durationNanos, boolean success, Map<String, Object> attributes) {
        try {
            String json = mapper.writeValueAsString(attributes == null ? Map.of() : attributes);
            jdbc.update("INSERT INTO rag_observation_event(trace_id, operation, doc_id, kb_id, conversation_id, duration_ms, success, attributes) VALUES(?,?,?,?,?,?,?,?::jsonb)",
                    String.valueOf(attributes == null ? "" : attributes.getOrDefault("trace_id", UUID.randomUUID().toString())),
                    operation, number(attributes, "doc_id"), number(attributes, "kb_id"), number(attributes, "conversation_id"),
                    durationNanos / 1_000_000D, success, json);
        } catch (Exception failure) {
            log.debug("观测事件持久化失败: operation={}", operation, failure);
        }
    }

    private static Long number(Map<String, Object> attributes, String key) {
        if (attributes == null || attributes.get(key) == null) return null;
        try { return Long.valueOf(String.valueOf(attributes.get(key))); } catch (Exception ignored) { return null; }
    }

    private void sendLangfuse(String operation, long durationNanos, boolean success, Map<String, Object> attributes) {
        CompletableFuture.runAsync(() -> {
            try {
                Instant end = Instant.now();
                Instant start = end.minusNanos(durationNanos);
                String traceId = attributes == null ? UUID.randomUUID().toString() : String.valueOf(attributes.getOrDefault("trace_id", UUID.randomUUID().toString()));
                Map<String, Object> body = Map.of("batch", java.util.List.of(Map.of(
                        "id", UUID.randomUUID().toString(), "type", "span-create",
                        "body", Map.of("traceId", traceId, "name", operation,
                                "startTime", start.toString(), "endTime", end.toString(),
                                "metadata", attributes == null ? Map.of() : attributes,
                                "output", Map.of("success", success)))));
                langfuse.post().uri("/api/public/ingestion")
                        .header("Authorization", "Basic " + auth)
                        .contentType(MediaType.APPLICATION_JSON).body(body).retrieve().toBodilessEntity();
            } catch (Exception failure) {
                log.debug("Langfuse 上报失败: operation={}", operation, failure);
            }
        });
    }

    public final class Span implements AutoCloseable {
        private final String operation;
        private final Map<String, Object> attributes;
        private final String previousTrace;
        private final long started = System.nanoTime();
        private boolean success;
        private boolean closed;

        private Span(String operation, Map<String, Object> attributes) {
            this.operation = operation;
            this.attributes = new LinkedHashMap<>(attributes);
            this.previousTrace = activeTrace.get();
            this.attributes.putIfAbsent("trace_id", previousTrace == null ? UUID.randomUUID().toString() : previousTrace);
            activeTrace.set(String.valueOf(this.attributes.get("trace_id")));
        }

        public String traceId() { return String.valueOf(attributes.get("trace_id")); }

        public void success() { success = true; }
        public void tag(String key, Object value) { if (key != null && value != null) attributes.put(key, value); }
        public void failure(Throwable error) {
            success = false;
            if (error != null) attributes.put("error_type", error.getClass().getSimpleName());
        }

        @Override public void close() {
            if (closed) return;
            closed = true;
            record(operation, System.nanoTime() - started, success, attributes);
            if (previousTrace == null) activeTrace.remove(); else activeTrace.set(previousTrace);
        }
    }
}
