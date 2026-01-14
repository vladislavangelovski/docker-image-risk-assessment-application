package com.finki.vladislavangelovski.gateway_service.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class RequestLoggingFilter implements GlobalFilter, Ordered {

    private static final Logger LOGGER = LoggerFactory.getLogger(RequestLoggingFilter.class);

    @Override
    public int getOrder() {
        return -1;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String requestId = request.getHeaders().getFirst(RequestIdFilter.HEADER);
        LOGGER.info("Incoming request: {} {} requestId={}", request.getMethod(), request.getURI(), requestId);
        return chain
                .filter(exchange)
                .then(Mono.fromRunnable(() -> LOGGER.info(
                        "Request completed with status {} requestId={}",
                        exchange.getResponse().getStatusCode(),
                        requestId)));
    }
}
