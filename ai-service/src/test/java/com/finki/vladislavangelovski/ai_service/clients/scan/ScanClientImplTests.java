package com.finki.vladislavangelovski.ai_service.clients.scan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.finki.vladislavangelovski.ai_service.clients.scan.exception.ScanClientException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.HttpMessageWriter;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.client.reactive.MockClientHttpRequest;
import org.springframework.web.reactive.function.BodyInserter;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class ScanClientImplTests {

  @Test
  void postsScanRequestWhenNotCached() throws InterruptedException {
    RecordingExchangeFunction exchange =
        new RecordingExchangeFunction(
            req -> {
              if (req.method() == HttpMethod.GET && "/api/v1/scans".equals(req.url().getPath())) {
                return json(HttpStatus.NOT_FOUND, "{}");
              }
              if (req.method() == HttpMethod.POST && "/api/v1/scans".equals(req.url().getPath())) {
                return json(
                    HttpStatus.OK,
                    "{"
                        + "\"image\":\"nginx:1.25\","
                        + "\"findings\":[{"
                        + "\"cveId\":\"CVE-1\",\"package\":\"openssl\"}]}");
              }
              return json(HttpStatus.NOT_FOUND, "{}");
            });

    ScanClientImpl client = new ScanClientImpl(webClient(exchange), "/api/v1/scans");

    var result = client.scanImage("nginx:1.25");

    CapturedRequest lookup = exchange.requests.get(0);
    assertThat(lookup.method()).isEqualTo("GET");
    assertThat(lookup.pathWithQuery()).startsWith("/api/v1/scans?imageRef=nginx");
    assertThat(lookup.pathWithQuery()).contains("1.25");

    CapturedRequest request = exchange.requests.get(1);
    assertThat(request.method()).isEqualTo("POST");
    assertThat(request.pathWithQuery()).isEqualTo("/api/v1/scans");
    assertThat(Objects.requireNonNull(request.body())).contains("\"image\":\"nginx:1.25\"");

    assertThat(result).isNotNull();
    assertThat(result.imageRef()).isEqualTo("nginx:1.25");
    assertThat(result.findings()).hasSize(1);
    assertThat(result.findings().getFirst().cveId()).isEqualTo("CVE-1");
  }

  @Test
  void reusesCachedScanWhenAvailable() throws InterruptedException {
    RecordingExchangeFunction exchange =
        new RecordingExchangeFunction(
            req ->
                json(
                    HttpStatus.OK,
                    "{"
                        + "\"image\":\"nginx:1.25\","
                        + "\"findings\":[{"
                        + "\"cveId\":\"CVE-1\",\"package\":\"openssl\"}]}"));

    ScanClientImpl client = new ScanClientImpl(webClient(exchange), "/api/v1/scans");

    var result = client.scanImage("nginx:1.25");

    CapturedRequest request = exchange.requests.getFirst();
    assertThat(request.method()).isEqualTo("GET");
    assertThat(request.pathWithQuery()).startsWith("/api/v1/scans?imageRef=nginx");
    assertThat(request.pathWithQuery()).contains("1.25");
    assertThat(exchange.requests).hasSize(1);

    assertThat(result).isNotNull();
    assertThat(result.imageRef()).isEqualTo("nginx:1.25");
    assertThat(result.findings()).hasSize(1);
    assertThat(result.findings().getFirst().cveId()).isEqualTo("CVE-1");
  }

  @Test
  void surfacesScanErrorsWithoutFallback() {
    RecordingExchangeFunction exchange =
        new RecordingExchangeFunction(
            req -> {
              if (req.method() == HttpMethod.GET && "/api/v1/scans".equals(req.url().getPath())) {
                return json(HttpStatus.NOT_FOUND, "{}");
              }
              if (req.method() == HttpMethod.POST && "/api/v1/scans".equals(req.url().getPath())) {
                return json(HttpStatus.INTERNAL_SERVER_ERROR, "{\"error\":\"boom\"}");
              }
              return json(HttpStatus.NOT_FOUND, "{}");
            });

    ScanClientImpl client = new ScanClientImpl(webClient(exchange), "/api/v1/scans");

    assertThrows(ScanClientException.class, () -> client.scanImage("nginx:latest"));
    assertThat(exchange.requests).hasSize(2);
  }

  private WebClient webClient(ExchangeFunction exchangeFunction) {
    return WebClient.builder().baseUrl("http://example").exchangeFunction(exchangeFunction).build();
  }

  private record CapturedRequest(String method, String pathWithQuery, String body) {}

  private static final class RecordingExchangeFunction implements ExchangeFunction {
    private final java.util.function.Function<ClientRequest, ClientResponse> responder;
    private final List<CapturedRequest> requests = new ArrayList<>();
    private final BodyInserter.Context inserterContext;

    private RecordingExchangeFunction(
        java.util.function.Function<ClientRequest, ClientResponse> responder) {
      this.responder = responder;
      ExchangeStrategies strategies = ExchangeStrategies.withDefaults();
      this.inserterContext =
          new BodyInserter.Context() {
            @Override
            public List<HttpMessageWriter<?>> messageWriters() {
              return strategies.messageWriters();
            }

            @Override
            public java.util.Optional<ServerHttpRequest> serverRequest() {
              return java.util.Optional.empty();
            }

            @Override
            public java.util.Map<String, Object> hints() {
              return java.util.Map.of();
            }
          };
    }

    @Override
    public Mono<ClientResponse> exchange(ClientRequest request) {
      String body = null;
      try {
        MockClientHttpRequest mock = new MockClientHttpRequest(request.method(), request.url());
        request.body().insert(mock, inserterContext).block();
        body = mock.getBodyAsString().block();
      } catch (Exception ignored) {
        // best-effort for assertions
      }

      String pathWithQuery = request.url().getRawPath();
      if (request.url().getRawQuery() != null && !request.url().getRawQuery().isBlank()) {
        pathWithQuery += "?" + request.url().getRawQuery();
      }

      requests.add(new CapturedRequest(request.method().name(), pathWithQuery, body));
      return Mono.just(responder.apply(request));
    }
  }

  private static ClientResponse json(HttpStatus status, String body) {
    DefaultDataBufferFactory f = new DefaultDataBufferFactory();
    DataBuffer b = f.wrap(body.getBytes(StandardCharsets.UTF_8));
    return ClientResponse.create(status)
        .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
        .body(Flux.just(b))
        .build();
  }
}
