package com.finki.vladislavangelovski.ai_service.clients.cve;

import com.finki.vladislavangelovski.ai_service.clients.cve.impl.CveStoreClientImpl;
import com.finki.vladislavangelovski.common.dto.CveForEmbedding;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CveStoreClientImplTests {

    private MockWebServer server;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    void fetchesFirstPageForEmbeddingCandidates() throws Exception {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("{\"content\":[{\"cveId\":\"CVE-123\",\"description\":\"demo\"}]}"));

        CveStoreClientImpl client = new CveStoreClientImpl(webClient(),
                "/api/v1/cves/{cveId}",
                "/api/v1/cves/{cveId}/epss",
                "");

        List<CveForEmbedding> candidates = client.findCandidatesForEmbedding(1);

        RecordedRequest request = server.takeRequest();
        assertThat(request.getMethod()).isEqualTo("GET");
        assertThat(request.getPath()).isEqualTo("/api/v1/cves?page=0&size=1");

        assertThat(candidates).hasSize(1);
        assertThat(candidates.getFirst().cveId()).isEqualTo("CVE-123");
        assertThat(candidates.getFirst().description()).isEqualTo("demo");
    }

    private WebClient webClient() {
        return WebClient.builder().baseUrl(server.url("/").toString()).build();
    }
}
