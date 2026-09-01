package com.aoneng.rag.infra.parse;

import com.aoneng.rag.infra.config.DoclingSamplingProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.tika.exception.TikaException;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** HTTP client for the independent Docling service, with a Tika fallback. */
@Primary
@Component
public class DoclingDocParser implements StructuredDocumentParser {
    private final TikaDocParser tika;
    private final DoclingSamplingProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestClient client;

    public DoclingDocParser(TikaDocParser tika, DoclingSamplingProperties properties) {
        this.tika = tika;
        this.properties = properties;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Math.min(properties.timeoutSeconds(), 60) * 1000);
        factory.setReadTimeout(properties.timeoutSeconds() * 1000);
        this.client = RestClient.builder().baseUrl(properties.endpoint()).requestFactory(factory).build();
    }

    @Override
    public void validateUpload(InputStream input, String fileName, String extension) throws IOException {
        tika.validateUpload(input, fileName, extension);
    }

    @Override
    public String parse(InputStream input, String fileName, String extension)
            throws IOException, TikaException, SAXException {
        if (!properties.enabled()) return tika.parse(input, fileName, extension);
        Path source = Files.createTempFile("docling-source-", "." + extension);
        try {
            copyBounded(input, source);
            return parseStructured(Files.newInputStream(source), fileName, extension).text();
        } catch (Exception failure) {
            if (!properties.fallbackToTika()) throw new IOException("Docling service parse failed", failure);
            try (InputStream fallback = Files.newInputStream(source)) {
                return tika.parse(fallback, fileName, extension);
            }
        } finally {
            Files.deleteIfExists(source);
        }
    }

    @Override
    public List<ParsedPage> parsePdfPages(InputStream input) throws IOException {
        Path source = Files.createTempFile("docling-pdf-", ".pdf");
        try {
            copyBounded(input, source);
            if (properties.enabled()) {
                try (InputStream structuredInput = Files.newInputStream(source)) {
                    StructuredDocument document = parseStructured(structuredInput, "document.pdf", "pdf");
                    Map<Integer, StringBuilder> grouped = new HashMap<>();
                    for (Block block : document.blocks()) {
                        if (block.pageNo() != null && !block.text().isBlank())
                            grouped.computeIfAbsent(block.pageNo(), ignored -> new StringBuilder()).append(block.text()).append('\n');
                    }
                    List<ParsedPage> pages = grouped.entrySet().stream()
                            .map(entry -> new ParsedPage(entry.getKey(), entry.getValue().toString().trim()))
                            .sorted(Comparator.comparingInt(ParsedPage::pageNo)).toList();
                    if (!pages.isEmpty()) return pages;
                } catch (IOException ignored) {
                    // Tika compatibility path below.
                }
            }
            try (InputStream fallback = Files.newInputStream(source)) {
                return tika.parsePdfPages(fallback);
            }
        } finally {
            Files.deleteIfExists(source);
        }
    }

    @Override
    public StructuredDocument parseStructured(InputStream input, String fileName, String extension) throws IOException {
        if (!properties.enabled()) throw new IOException("Docling service is disabled");
        Path temp = Files.createTempFile("docling-", "." + extension);
        try {
            copyBounded(input, temp);
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", new FileSystemResource(temp));
            body.add("extension", extension);
            body.add("samplePages", properties.samplePages());
            body.add("sampleRows", properties.sampleRows());
            String responseBody = client.post().uri("/v1/parse")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body)
                    .retrieve()
                    .body(String.class);
            JsonNode response = responseBody == null ? null : objectMapper.readTree(responseBody);
            if (response == null || !response.has("text")) throw new IOException("Invalid Docling response");
            return fromJson(response);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private StructuredDocument fromJson(JsonNode root) {
        List<Block> blocks = new ArrayList<>();
        for (JsonNode node : root.path("blocks")) {
            Map<String, Object> metadata = objectMapper.convertValue(node.path("metadata"), Map.class);
            Integer page = node.hasNonNull("pageNo") ? node.get("pageNo").asInt() : null;
            blocks.add(new Block(node.path("type").asText("paragraph"), node.path("text").asText(""), page, metadata));
        }
        Map<String, Object> metadata = objectMapper.convertValue(root.path("metadata"), Map.class);
        return new StructuredDocument(root.path("text").asText(""), blocks, metadata);
    }

    private void copyBounded(InputStream input, Path target) throws IOException {
        try (var output = Files.newOutputStream(target)) {
            byte[] buffer = new byte[8192];
            long total = 0;
            int read;
            while ((read = input.read(buffer)) >= 0) {
                total += read;
                if (total > properties.maxBytes()) throw new IOException("Document exceeds Docling size limit");
                output.write(buffer, 0, read);
            }
        }
    }
}
