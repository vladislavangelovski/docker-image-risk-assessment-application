package com.finki.vladislavangelovski.gateway_service.filter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finki.vladislavangelovski.common.error.ErrorResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.reactivestreams.Publisher;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class GatewayErrorResponseFilter implements GlobalFilter, Ordered {
  private final ObjectMapper objectMapper;

  public GatewayErrorResponseFilter(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  public int getOrder() {
    return -2;
  }

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    ServerHttpResponse response = exchange.getResponse();
    ServerHttpResponseDecorator decorated =
        new ServerHttpResponseDecorator(response) {
          @Override
          public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
            HttpStatusCode status = getStatusCode();
            if (status == null || !status.isError()) {
              return super.writeWith(body);
            }
            if (body == null) {
              return super.writeWith(Mono.empty());
            }
            return DataBufferUtils.join(body)
                .flatMap(
                    buffer -> {
                      byte[] bytes = new byte[buffer.readableByteCount()];
                      buffer.read(bytes);
                      DataBufferUtils.release(buffer);

                      ErrorResponse normalized = buildError(exchange, status, bytes);
                      byte[] out;
                      try {
                        out = objectMapper.writeValueAsBytes(normalized);
                      } catch (Exception ex) {
                        String fallback =
                            "{\"status\":%d,\"message\":\"Upstream error\"}"
                                .formatted(status.value());
                        out = fallback.getBytes(StandardCharsets.UTF_8);
                      }
                      getHeaders().setContentType(MediaType.APPLICATION_JSON);
                      getHeaders().setContentLength(out.length);
                      return super.writeWith(Mono.just(response.bufferFactory().wrap(out)));
                    });
          }

          @Override
          public Mono<Void> writeAndFlushWith(
              Publisher<? extends Publisher<? extends DataBuffer>> body) {
            return writeWith(Flux.from(body).flatMapSequential(p -> p));
          }
        };
    return chain.filter(exchange.mutate().response(decorated).build());
  }

  private ErrorResponse buildError(ServerWebExchange exchange, HttpStatusCode status, byte[] body) {
    String requestId = exchange.getRequest().getHeaders().getFirst(RequestIdFilter.HEADER);
    String message = extractMessage(body);
    HttpStatus resolved = HttpStatus.resolve(status.value());
    if ((message == null || message.isBlank() || "Upstream error".equals(message))
        && resolved != null) {
      message = resolved.getReasonPhrase();
    }
    Map<String, Object> details = new LinkedHashMap<>();
    details.put("upstreamStatus", status.value());
    details.put("timestamp", Instant.now().toString());
    return ErrorResponse.of(
        status.value(), message, exchange.getRequest().getPath().value(), requestId, details);
  }

  private String extractMessage(byte[] body) {
    if (body == null || body.length == 0) {
      return "Upstream error";
    }
    String raw = new String(body, StandardCharsets.UTF_8).trim();
    if (raw.isBlank()) {
      return "Upstream error";
    }
    try {
      Map<String, Object> payload = objectMapper.readValue(raw, new TypeReference<>() {});
      for (String key : new String[] {"message", "detail", "error", "title"}) {
        Object value = payload.get(key);
        if (value != null && !value.toString().isBlank()) {
          return value.toString();
        }
      }
    } catch (Exception ignored) {
    }
    return "Upstream error";
  }
}
