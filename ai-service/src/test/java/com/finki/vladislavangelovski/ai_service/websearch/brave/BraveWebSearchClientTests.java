package com.finki.vladislavangelovski.ai_service.websearch.brave;

import static org.assertj.core.api.Assertions.assertThat;

import com.finki.vladislavangelovski.ai_service.websearch.WebSearchResult;
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

class BraveWebSearchClientTests {

  @Test
  void sendsApiKeyAndParsesResults() throws Exception {
    RecordingExchangeFunction exchange =
        new RecordingExchangeFunction(
            req ->
                json(
                    HttpStatus.OK,
                    """
                    {
                      "web": {
                        "results": [
                          {
                            "title": "How to fix CVE-2021-44228",
                            "url": "https://example.com/fix-log4shell",
                            "description": "Upgrade Log4j to 2.17.1+ and set formatMsgNoLookups."
                          }
                        ]
                      }
                    }
                    """));

    WebClient webClient =
        WebClient.builder().baseUrl("http://example").exchangeFunction(exchange).build();
    BraveWebSearchClient client = new BraveWebSearchClient(webClient, "test-token");

    List<WebSearchResult> results = client.search("CVE-2021-44228 remediation", 3);

    assertThat(results).hasSize(1);
    assertThat(results.getFirst().title()).contains("CVE-2021-44228");
    assertThat(results.getFirst().url()).isEqualTo("https://example.com/fix-log4shell");
    assertThat(results.getFirst().snippet()).contains("Upgrade Log4j");

    assertThat(exchange.requests).hasSize(1);
    assertThat(exchange.requests.getFirst().method()).isEqualTo("GET");
    assertThat(exchange.requests.getFirst().pathWithQuery()).contains("/res/v1/web/search");
    assertThat(exchange.requests.getFirst().pathWithQuery()).contains("q=CVE-2021-44228");
    assertThat(exchange.requests.getFirst().pathWithQuery()).contains("count=3");
    assertThat(exchange.requests.getFirst().braveToken()).isEqualTo("test-token");
    assertThat(exchange.requests.getFirst().accept()).contains(MediaType.APPLICATION_JSON_VALUE);
  }

  private record CapturedRequest(
      String method, String pathWithQuery, String braveToken, String accept) {}

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
      String braveToken = request.headers().getFirst("X-Subscription-Token");
      String accept = request.headers().getFirst("Accept");
      requests.add(new CapturedRequest(request.method().name(), pathWithQuery, braveToken, accept));
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
