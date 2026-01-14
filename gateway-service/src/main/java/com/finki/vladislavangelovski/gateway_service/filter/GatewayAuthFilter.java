package com.finki.vladislavangelovski.gateway_service.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finki.vladislavangelovski.common.error.ErrorResponse;
import com.finki.vladislavangelovski.gateway_service.config.GatewayAuthProperties;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class GatewayAuthFilter implements GlobalFilter, Ordered {
  private final GatewayAuthProperties properties;
  private final ObjectMapper objectMapper;

  public GatewayAuthFilter(GatewayAuthProperties properties, ObjectMapper objectMapper) {
    this.properties = properties;
    this.objectMapper = objectMapper;
  }

  @Override
  public int getOrder() {
    return -4;
  }

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    if (!properties.isEnabled()) {
      return chain.filter(exchange);
    }

    String path = exchange.getRequest().getPath().value();
    if (isExcluded(path)) {
      return chain.filter(exchange);
    }

    String headerName = properties.getHeader();
    String expected = properties.getApiKey();
    String provided = exchange.getRequest().getHeaders().getFirst(headerName);

    if (expected == null || expected.isBlank() || provided == null || !provided.equals(expected)) {
      return writeUnauthorized(exchange);
    }

    return chain.filter(exchange);
  }

  private boolean isExcluded(String path) {
    if (path == null) {
      return false;
    }
    for (String prefix : properties.getExcludedPaths()) {
      if (prefix != null && !prefix.isBlank() && path.startsWith(prefix)) {
        return true;
      }
    }
    return false;
  }

  private Mono<Void> writeUnauthorized(ServerWebExchange exchange) {
    ServerHttpResponse response = exchange.getResponse();
    response.setStatusCode(HttpStatus.UNAUTHORIZED);
    response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

    String requestId = exchange.getRequest().getHeaders().getFirst("X-Request-Id");
    Map<String, Object> details = new LinkedHashMap<>();
    details.put("auth", "apiKey");
    ErrorResponse body =
        ErrorResponse.of(
            HttpStatus.UNAUTHORIZED.value(),
            "Unauthorized",
            exchange.getRequest().getPath().value(),
            requestId,
            details);

    byte[] bytes;
    try {
      bytes = objectMapper.writeValueAsBytes(body);
    } catch (Exception ex) {
      String fallback = "{\"status\":401,\"message\":\"Unauthorized\"}";
      bytes = fallback.getBytes(StandardCharsets.UTF_8);
    }
    return response.writeWith(Mono.just(response.bufferFactory().wrap(bytes)));
  }
}
