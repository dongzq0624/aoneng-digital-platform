package com.aoneng.rag.infra.parse;

import com.aoneng.rag.infra.config.PaddleOcrProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Client for the independent PaddleOCR service. */
@Component
public class PaddleOcrClient {
    private final PaddleOcrProperties properties;
    private final ObjectMapper mapper = new ObjectMapper();
    private final RestClient client;

    public PaddleOcrClient(PaddleOcrProperties properties) {
        this.properties = properties;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Math.min(properties.timeoutSeconds(), 60) * 1000);
        factory.setReadTimeout(properties.timeoutSeconds() * 1000);
        this.client = RestClient.builder().baseUrl(properties.endpoint()).requestFactory(factory).build();
    }

    public boolean enabled() { return properties.enabled(); }

    public StructuredDocumentParser.StructuredDocument parseStructured(InputStream input, String fileName) throws IOException {
        if (!properties.enabled()) throw new IOException("PaddleOCR is disabled");
        String suffix = fileName == null || !fileName.contains(".") ? ".pdf" : fileName.substring(fileName.lastIndexOf('.'));
        Path source = Files.createTempFile("paddleocr-", suffix);
        try {
            try (var output = Files.newOutputStream(source)) { input.transferTo(output); }
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", new FileSystemResource(source));
            byte[] responseBytes = client.post().uri("/v1/ocr/pdf").contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body).retrieve().body(byte[].class);
            String responseBody = responseBytes == null ? null : new String(responseBytes, java.nio.charset.StandardCharsets.UTF_8);
            JsonNode root = responseBody == null ? null : mapper.readTree(responseBody);
            if (root == null || !root.has("text") || !root.has("blocks")) throw new IOException("Invalid PaddleOCR response");
            List<StructuredDocumentParser.Block> blocks = new ArrayList<>();
            for (JsonNode node : root.path("blocks")) {
                Map<String,Object> metadata = mapper.convertValue(node.path("metadata"), Map.class);
                Map<String,Object> bbox = mapper.convertValue(node.path("bbox"), Map.class);
                Integer page = node.hasNonNull("pageNo") ? node.get("pageNo").asInt() : null;
                Integer order = node.hasNonNull("order") ? node.get("order").asInt() : blocks.size();
                blocks.add(new StructuredDocumentParser.Block("paragraph", node.path("text").asText(""), page,
                        0, order, bbox, metadata));
            }
            return new StructuredDocumentParser.StructuredDocument(root.path("text").asText(""), blocks,
                    mapper.convertValue(root.path("metadata"), Map.class));
        } finally { Files.deleteIfExists(source); }
    }
}
