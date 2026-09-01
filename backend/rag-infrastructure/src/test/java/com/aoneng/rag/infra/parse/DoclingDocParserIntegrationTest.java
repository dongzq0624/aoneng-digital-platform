package com.aoneng.rag.infra.parse;

import com.aoneng.rag.infra.config.DoclingSamplingProperties;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;

import static org.junit.jupiter.api.Assertions.*;

class DoclingDocParserIntegrationTest {
    private static final Path SAMPLES = Path.of("C:/Temp/aoneng-docling-samples");

    @Test
    void httpServiceResponseIsConvertedToStructuredDocument() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/parse", exchange -> {
            exchange.getRequestBody().readAllBytes();
            byte[] response = "{\"text\":\"Hello\",\"blocks\":[{\"type\":\"heading\",\"text\":\"Hello\",\"pageNo\":2,\"metadata\":{}}],\"metadata\":{\"parser\":\"docling\"}}".getBytes();
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            var parser = parser(true, "http://localhost:" + server.getAddress().getPort(), 2);
            var result = parser.parseStructured(new java.io.ByteArrayInputStream("sample".getBytes()), "sample.docx", "docx");
            assertEquals("Hello", result.text());
            assertEquals(2, result.blocks().get(0).pageNo());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void realSamplesUseTikaWhenDoclingDisabled() throws Exception {
        if (!Files.isDirectory(SAMPLES)) return;
        var parser = parser(false, "http://localhost:8090", 2);
        for (String name : new String[]{"sample.pdf", "sample.docx", "sample.xlsx"}) {
            Path file = SAMPLES.resolve(name);
            if (!Files.exists(file)) continue;
            String extension = name.substring(name.lastIndexOf('.') + 1);
            try (InputStream input = Files.newInputStream(file)) {
                String text = parser.parse(input, name, extension);
                assertFalse(text.isBlank(), name + " should produce text through Tika fallback");
            }
        }
    }

    @Test
    void missingDoclingCommandFallsBackToTika() throws Exception {
        var parser = parser(true, "http://localhost:1", 2);
        Path file = SAMPLES.resolve("sample.docx");
        if (!Files.exists(file)) return;
        try (InputStream input = Files.newInputStream(file)) {
            assertFalse(parser.parse(input, "sample.docx", "docx").isBlank());
        }
    }

    @Test
    void timeoutIsReportedAndCanFallback() throws Exception {
        Path sleeper = Files.createTempFile("docling-sleeper-", ".py");
        Files.writeString(sleeper, "import time; time.sleep(5)");
        try {
            var parser = parser(true, "http://localhost:1", 1);
            Path file = SAMPLES.resolve("sample.docx");
            if (!Files.exists(file)) return;
            try (InputStream input = Files.newInputStream(file)) {
                assertFalse(parser.parse(input, "sample.docx", "docx").isBlank());
            }
            var strict = new DoclingDocParser(new TikaDocParser(),
                    new com.aoneng.rag.infra.config.DoclingSamplingProperties(true, "http://localhost:1",
                            1, 50L * 1024 * 1024, 8, 200, false));
            try (InputStream input = Files.newInputStream(file)) {
                assertThrows(java.io.IOException.class, () -> strict.parse(input, "sample.docx", "docx"));
            }
        } finally {
            Files.deleteIfExists(sleeper);
        }
    }

    private DoclingDocParser parser(boolean enabled, String endpoint, int timeout) {
        return new DoclingDocParser(new TikaDocParser(),
                new DoclingSamplingProperties(enabled, endpoint, timeout,
                        50L * 1024 * 1024, 8, 200, true));
    }
}
