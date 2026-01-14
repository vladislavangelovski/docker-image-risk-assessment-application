package com.finki.vladislavangelovski.ai_service.clients.scan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.finki.vladislavangelovski.ai_service.clients.scan.exception.ScanClientException;
import java.io.IOException;
import java.util.Objects;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

class ScanClientImplTests {

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
  void postsScanRequestWhenNotCached() throws InterruptedException {
    server.enqueue(new MockResponse().setResponseCode(404));
    server.enqueue(
        new MockResponse()
            .setHeader("Content-Type", "application/json")
            .setBody(
                "{"
                    + "\"image\":\"nginx:1.25\","
                    + "\"findings\":[{"
                    + "\"cveId\":\"CVE-1\",\"package\":\"openssl\"}]}"));

    ScanClientImpl client = new ScanClientImpl(webClient(), "/api/v1/scans");

    var result = client.scanImage("nginx:1.25");

    RecordedRequest lookup = server.takeRequest();
    assertThat(lookup.getMethod()).isEqualTo("GET");
    assertThat(lookup.getPath()).isEqualTo("/api/v1/scans?imageRef=nginx:1.25");

    RecordedRequest request = server.takeRequest();
    assertThat(request.getMethod()).isEqualTo("POST");
    assertThat(request.getPath()).isEqualTo("/api/v1/scans");
    assertThat(Objects.requireNonNull(request.getBody().readUtf8()))
        .contains("\"image\":\"nginx:1.25\"");

    assertThat(result).isNotNull();
    assertThat(result.imageRef()).isEqualTo("nginx:1.25");
    assertThat(result.findings()).hasSize(1);
    assertThat(result.findings().getFirst().cveId()).isEqualTo("CVE-1");
  }

  @Test
  void reusesCachedScanWhenAvailable() throws InterruptedException {
    server.enqueue(
        new MockResponse()
            .setHeader("Content-Type", "application/json")
            .setBody(
                "{"
                    + "\"image\":\"nginx:1.25\","
                    + "\"findings\":[{"
                    + "\"cveId\":\"CVE-1\",\"package\":\"openssl\"}]}"));

    ScanClientImpl client = new ScanClientImpl(webClient(), "/api/v1/scans");

    var result = client.scanImage("nginx:1.25");

    RecordedRequest request = server.takeRequest();
    assertThat(request.getMethod()).isEqualTo("GET");
    assertThat(request.getPath()).isEqualTo("/api/v1/scans?imageRef=nginx:1.25");
    assertThat(server.getRequestCount()).isEqualTo(1);

    assertThat(result).isNotNull();
    assertThat(result.imageRef()).isEqualTo("nginx:1.25");
    assertThat(result.findings()).hasSize(1);
    assertThat(result.findings().getFirst().cveId()).isEqualTo("CVE-1");
  }

  @Test
  void surfacesScanErrorsWithoutFallback() {
    server.enqueue(new MockResponse().setResponseCode(404));
    server.enqueue(
        new MockResponse()
            .setResponseCode(500)
            .setHeader("Content-Type", "application/json")
            .setBody("{\"error\":\"boom\"}"));

    ScanClientImpl client = new ScanClientImpl(webClient(), "/api/v1/scans");

    assertThrows(ScanClientException.class, () -> client.scanImage("nginx:latest"));
    assertThat(server.getRequestCount()).isEqualTo(2);
  }

  private WebClient webClient() {
    return WebClient.builder().baseUrl(server.url("/").toString()).build();
  }
}
