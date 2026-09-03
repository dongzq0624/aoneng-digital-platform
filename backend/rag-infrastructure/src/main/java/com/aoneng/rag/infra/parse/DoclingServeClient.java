package com.aoneng.rag.infra.parse;

import com.aoneng.rag.infra.config.DoclingSamplingProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.tika.exception.TikaException;
import org.springframework.core.io.FileSystemResource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Strict client for the versioned Docling-Serve HTTP contract, with Tika fallback. */
@Primary
@Component
public class DoclingServeClient implements StructuredDocumentParser {
    private final TikaDocParser tika;
    private final DoclingSamplingProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestClient client;
    private final PaddleOcrClient paddleOcr;

    public DoclingServeClient(TikaDocParser tika, DoclingSamplingProperties properties) {
        this(tika, properties, null);
    }

    @Autowired
    public DoclingServeClient(TikaDocParser tika, DoclingSamplingProperties properties, PaddleOcrClient paddleOcr) {
        this.tika = tika;
        this.properties = properties;
        this.paddleOcr = paddleOcr;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Math.min(properties.timeoutSeconds(), 60) * 1000);
        factory.setReadTimeout(properties.timeoutSeconds() * 1000);
        this.client = RestClient.builder().baseUrl(properties.endpoint()).requestFactory(factory).build();
    }

    @Override public void validateUpload(InputStream input, String fileName, String extension) throws IOException {
        tika.validateUpload(input, fileName, extension);
    }

    @Override public String parse(InputStream input, String fileName, String extension)
            throws IOException, TikaException, SAXException {
        if (!properties.enabled()) return tika.parse(input, fileName, extension);
        Path source = Files.createTempFile("docling-source-", "." + extension);
        try {
            copyBounded(input, source);
            return parseStructured(Files.newInputStream(source), fileName, extension).text();
        } catch (Exception failure) {
            if (!properties.fallbackToTika()) throw new IOException("Docling-Serve parse failed", failure);
            try (InputStream fallback = Files.newInputStream(source)) { return tika.parse(fallback, fileName, extension); }
        } finally { Files.deleteIfExists(source); }
    }

    /** Parse directly with Tika after the Docling attempt has already timed out. */
    public String parseWithTika(InputStream input, String fileName, String extension)
            throws IOException, TikaException, SAXException {
        return tika.parse(input, fileName, extension);
    }

    @Override public List<ParsedPage> parsePdfPages(InputStream input) throws IOException {
        Path source = Files.createTempFile("docling-pdf-", ".pdf");
        try {
            copyBounded(input, source);
            if (properties.enabled()) {
                try (InputStream structuredInput = Files.newInputStream(source)) {
                    StructuredDocument document = parseStructured(structuredInput, "document.pdf", "pdf");
                    Map<Integer, StringBuilder> grouped = new HashMap<>();
                    for (Block block : document.blocks()) if (block.pageNo() != null && !block.text().isBlank())
                        grouped.computeIfAbsent(block.pageNo(), ignored -> new StringBuilder()).append(block.text()).append('\n');
                    List<ParsedPage> pages = grouped.entrySet().stream()
                            .map(e -> new ParsedPage(e.getKey(), e.getValue().toString().trim()))
                            .sorted(Comparator.comparingInt(ParsedPage::pageNo)).toList();
                    if (!pages.isEmpty()) return pages;
                } catch (Exception failure) {
                    if (!properties.fallbackToTika()) throw new IOException("Docling-Serve PDF parse failed", failure);
                    if (paddleOcr != null && paddleOcr.enabled()) {
                        try (InputStream ocrInput = Files.newInputStream(source)) {
                            var ocr = paddleOcr.parseStructured(ocrInput, "document.pdf");
                            List<ParsedPage> pages = pagesOf(ocr);
                            if (!pages.isEmpty()) return pages;
                        } catch (Exception ocrFailure) {
                            // Tika remains the final compatibility parser.
                        }
                    }
                }
            }
            try (InputStream fallback = Files.newInputStream(source)) { return tika.parsePdfPages(fallback); }
        } finally { Files.deleteIfExists(source); }
    }

    private List<ParsedPage> pagesOf(StructuredDocument document) {
        Map<Integer, StringBuilder> grouped = new HashMap<>();
        for (Block block : document.blocks()) if (block.pageNo() != null && !block.text().isBlank())
            grouped.computeIfAbsent(block.pageNo(), ignored -> new StringBuilder()).append(block.text()).append('\n');
        return grouped.entrySet().stream().map(e -> new ParsedPage(e.getKey(), e.getValue().toString().trim()))
                .sorted(Comparator.comparingInt(ParsedPage::pageNo)).toList();
    }

    @Override public StructuredDocument parseStructured(InputStream input, String fileName, String extension) throws IOException {
        if (!properties.enabled()) throw new IOException("Docling-Serve is disabled");
        Path temp = Files.createTempFile("docling-", "." + extension);
        try {
            copyBounded(input, temp);
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", new FileSystemResource(temp)); body.add("extension", extension);
            body.add("samplePages", properties.samplePages()); body.add("sampleRows", properties.sampleRows());
            byte[] responseBytes = client.post().uri("/v1/parse").contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body)
                    // Docling-Serve may label JSON as application/octet-stream. Read the
                    // response stream directly instead of relying on an HTTP message converter.
                    .exchange((request, response) -> {
                        if (!response.getStatusCode().is2xxSuccessful()) {
                            throw new IOException("Docling-Serve returned HTTP " + response.getStatusCode().value());
                        }
                        try (InputStream responseBody = response.getBody()) {
                            return responseBody.readAllBytes();
                        }
                    });
            String responseBody = responseBytes == null ? null : new String(responseBytes, java.nio.charset.StandardCharsets.UTF_8);
            JsonNode response = responseBody == null ? null : objectMapper.readTree(responseBody);
            if (response == null || !response.has("text") || !response.has("blocks")) throw new IOException("Invalid Docling-Serve response");
            return fromJson(response);
        } finally { Files.deleteIfExists(temp); }
    }

    private StructuredDocument fromJson(JsonNode root) {
        List<Block> blocks = new ArrayList<>();
        for (JsonNode node : root.path("blocks")) {
            Map<String,Object> metadata = objectMapper.convertValue(node.path("metadata"), Map.class);
            Map<String,Object> bbox = objectMapper.convertValue(node.path("bbox"), Map.class);
            Integer page = node.hasNonNull("pageNo") ? node.get("pageNo").asInt() : null;
            Integer level = node.hasNonNull("level") ? node.get("level").asInt() : 0;
            Integer order = node.hasNonNull("order") ? node.get("order").asInt() : 0;
            blocks.add(new Block(node.path("type").asText("paragraph"), node.path("text").asText(""), page, level, order, bbox, metadata));
        }
        return new StructuredDocument(root.path("text").asText(""), blocks,
                objectMapper.convertValue(root.path("metadata"), Map.class));
    }

    private void copyBounded(InputStream input, Path target) throws IOException {
        try (var output = Files.newOutputStream(target)) {
            byte[] buffer = new byte[8192]; long total = 0; int read;
            while ((read = input.read(buffer)) >= 0) { total += read; if (total > properties.maxBytes()) throw new IOException("Document exceeds Docling size limit"); output.write(buffer, 0, read); }
        }
    }
}
