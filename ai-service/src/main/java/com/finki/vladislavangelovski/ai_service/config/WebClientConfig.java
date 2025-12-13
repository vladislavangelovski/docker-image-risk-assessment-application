package com.finki.vladislavangelovski.ai_service.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.util.retry.Retry;

@Configuration
@Slf4j
public class WebClientConfig {

    private final int connectTimeoutMs;
    private final int responseTimeoutMs;
    private final int readTimeoutMs;
    private final int writeTimeoutMs;
    private final int retryMaxAttempts;
    private final int retryInitialBackoffMs;
    private final int retryMaxBackoffMs;

    public WebClientConfig(
            @Value("${services.http.connect-timeout-ms:2000}") int connectTimeoutMs,
            @Value("${services.http.response-timeout-ms:5000}") int responseTimeoutMs,
            @Value("${services.http.read-timeout-ms:5000}") int readTimeoutMs,
            @Value("${services.http.write-timeout-ms:5000}") int writeTimeoutMs,
            @Value("${services.http.retry.max-attempts:3}") int retryMaxAttempts,
            @Value("${services.http.retry.initial-backoff-ms:2000}") int retryInitialBackoffMs,
            @Value("${services.http.retry.max-backoff-ms:10000}") int retryMaxBackoffMs) {
        this.connectTimeoutMs = connectTimeoutMs;
        this.responseTimeoutMs = responseTimeoutMs;
        this.readTimeoutMs = readTimeoutMs;
        this.writeTimeoutMs = writeTimeoutMs;
        this.retryMaxAttempts = retryMaxAttempts;
        this.retryInitialBackoffMs = retryInitialBackoffMs;
        this.retryMaxBackoffMs = retryMaxBackoffMs;
    }

    @Bean
    public WebClient scanWebClient(@Value("${services.scan.base-url}") String baseUrl) {
        return baseWebClientBuilder(2 * 1024 * 1024).baseUrl(baseUrl).build();
    }

    @Bean
    public WebClient cveStoreWebClient(@Value("${services.cvestore.base-url}") String baseUrl) {
        return baseWebClientBuilder(4 * 1024 * 1024).baseUrl(baseUrl).build();
    }

    @Bean("embeddingsWebClient")
    public WebClient embeddingsWebClient(@Value("${embeddings.base-url}") String baseUrl) {
        return WebClient.builder().baseUrl(baseUrl).build();
    }

    private WebClient.Builder baseWebClientBuilder(int maxInMemoryBytes) {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeoutMs)
                .responseTimeout(Duration.ofMillis(responseTimeoutMs))
                .doOnConnected(conn -> conn.addHandlerLast(new ReadTimeoutHandler(readTimeoutMs, TimeUnit.MILLISECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(writeTimeoutMs, TimeUnit.MILLISECONDS)));

        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .exchangeStrategies(ExchangeStrategies.builder()
                        .codecs(c -> c.defaultCodecs().maxInMemorySize(maxInMemoryBytes))
                        .build())
                .filter(retryFilter());
    }

    private ExchangeFilterFunction retryFilter() {
        Retry retrySpec = Retry.backoff(retryMaxAttempts, Duration.ofMillis(retryInitialBackoffMs))
                .maxBackoff(Duration.ofMillis(retryMaxBackoffMs))
                .filter(this::isRetryable)
                .doBeforeRetry(rs -> {
                    long exponentialDelay = (long) (retryInitialBackoffMs * Math.pow(2, rs.totalRetries()));
                    long cappedDelay = Math.min(exponentialDelay, retryMaxBackoffMs);
                    log.warn("Retrying {} after {} ms (attempt {}/{})", rs.failure().getClass().getSimpleName(), cappedDelay,
                            rs.totalRetries() + 1, retryMaxAttempts);
                });

        return (request, next) -> next.exchange(request)
                .flatMap(this::propagate5xxToError)
                .retryWhen(retrySpec);
    }

    private boolean isRetryable(Throwable throwable) {
        return throwable instanceof WebClientRequestException
                || (throwable instanceof WebClientResponseException response
                        && response.getStatusCode().is5xxServerError());
    }

    private Mono<ClientResponse> propagate5xxToError(ClientResponse response) {
        if (response.statusCode().is5xxServerError()) {
            return response.createException().flatMap(Mono::error);
        }
        return Mono.just(response);
    }
}
