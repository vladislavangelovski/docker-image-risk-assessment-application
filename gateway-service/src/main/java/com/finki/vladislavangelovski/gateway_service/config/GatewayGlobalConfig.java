package com.finki.vladislavangelovski.gateway_service.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finki.vladislavangelovski.common.error.ErrorResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Configuration
public class GatewayGlobalConfig {

  private static final Logger LOGGER = LoggerFactory.getLogger(GatewayGlobalConfig.class);
  private final ObjectMapper objectMapper;

  public GatewayGlobalConfig(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Bean
  public GlobalFilter errorHandlingFilter() {
    return (exchange, chain) ->
        chain.filter(exchange).onErrorResume(ex -> handleError(exchange, ex));
  }

  private Mono<Void> handleError(ServerWebExchange exchange, Throwable ex) {
    LOGGER.error("Unhandled exception in gateway filter chain", ex);

    var response = exchange.getResponse();
    if (response.isCommitted()) {
      return Mono.error(ex);
    }

    response.setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
    response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
    ErrorResponse error =
        ErrorResponse.of(
            HttpStatus.SERVICE_UNAVAILABLE.value(),
            "Upstream service unavailable",
            exchange.getRequest().getPath().value(),
            exchange.getRequest().getId(),
            Map.of());

    byte[] body;
    try {
      body = objectMapper.writeValueAsBytes(error);
    } catch (JsonProcessingException jsonException) {
      String fallback = "{\"status\":503,\"title\":\"Gateway Error\"}";
      body = fallback.getBytes(StandardCharsets.UTF_8);
    }
    return response.writeWith(Mono.just(response.bufferFactory().wrap(body)));
  }
}
