package com.finki.vladislavangelovski.ai_service.clients.cve;

import static org.assertj.core.api.Assertions.assertThat;

import com.finki.vladislavangelovski.ai_service.clients.cve.impl.CveStoreClientImpl;
import com.finki.vladislavangelovski.common.dto.CveForEmbedding;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class CveStoreClientImplTests {

  @Test
  void fetchesFirstPageForEmbeddingCandidates() throws Exception {
    RecordingExchangeFunction exchange =
        new RecordingExchangeFunction(
            req -> {
              String path = req.url().getRawPath();
              if ("/api/v1/cves".equals(path)) {
                return json(
                    HttpStatus.OK,
                    "{\"content\":[{\"cveId\":\"CVE-123\",\"description\":\"demo\"}]}");
              }
              return json(HttpStatus.NOT_FOUND, "{}");
            });

    CveStoreClientImpl client =
        new CveStoreClientImpl(
            webClient(exchange), "/api/v1/cves/{cveId}", "/api/v1/cves/{cveId}/epss", "");

    List<CveForEmbedding> candidates = client.findCandidatesForEmbedding(1);

    CapturedRequest request = exchange.requests.getFirst();
    assertThat(request.method()).isEqualTo("GET");
    assertThat(request.pathWithQuery()).startsWith("/api/v1/cves?");
    assertThat(request.pathWithQuery()).contains("page=0");
    assertThat(request.pathWithQuery()).contains("size=1");

    assertThat(candidates).hasSize(1);
    assertThat(candidates.getFirst().cveId()).isEqualTo("CVE-123");
    assertThat(candidates.getFirst().description()).isEqualTo("demo");
  }

  @Test
  void fetchesBatchCvesWhenAvailable() {
    RecordingExchangeFunction exchange =
        new RecordingExchangeFunction(
            req -> {
              if ("POST".equals(req.method().name())
                  && "/api/v1/cves/batch".equals(req.url().getRawPath())) {
                return json(
                    HttpStatus.OK,
                    """
                    [
                      {"cveId":"CVE-1","description":"demo-1","epssScore":0.1,"epssPercentile":10.0},
                      {"cveId":"CVE-2","description":"demo-2","epssScore":0.2,"epssPercentile":20.0}
                    ]
                    """);
              }
              return json(HttpStatus.NOT_FOUND, "{}");
            });

    CveStoreClientImpl client =
        new CveStoreClientImpl(
            webClient(exchange), "/api/v1/cves/{cveId}", "/api/v1/cves/{cveId}/epss", "");

    var out = client.getByIds(List.of("CVE-1", "CVE-2"));

    assertThat(exchange.requests).isNotEmpty();
    assertThat(exchange.requests.getFirst().method()).isEqualTo("POST");
    assertThat(exchange.requests.getFirst().pathWithQuery()).isEqualTo("/api/v1/cves/batch");

    assertThat(out).hasSize(2);
    assertThat(out.get("CVE-1").description()).isEqualTo("demo-1");
    assertThat(out.get("CVE-2").epss()).isEqualTo(0.2);
  }

  private WebClient webClient(ExchangeFunction exchangeFunction) {
    return WebClient.builder().baseUrl("http://example").exchangeFunction(exchangeFunction).build();
  }

  private record CapturedRequest(String method, String pathWithQuery) {}

  private static final class RecordingExchangeFunction implements ExchangeFunction {
    private final java.util.function.Function<ClientRequest, ClientResponse> responder;
    private final List<CapturedRequest> requests = new ArrayList<>();

    private RecordingExchangeFunction(
        java.util.function.Function<ClientRequest, ClientResponse> responder) {
      this.responder = responder;
    }

    @Override
    public Mono<ClientResponse> exchange(ClientRequest request) {
      String pathWithQuery = request.url().getRawPath();
      if (request.url().getRawQuery() != null && !request.url().getRawQuery().isBlank()) {
        pathWithQuery += "?" + request.url().getRawQuery();
      }
      requests.add(new CapturedRequest(request.method().name(), pathWithQuery));
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
