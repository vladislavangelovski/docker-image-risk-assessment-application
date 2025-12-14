package com.finki.vladislavangelovski.gateway_service.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Configuration
public class GatewayGlobalConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger(GatewayGlobalConfig.class);

    @Bean
    public CorsWebFilter corsWebFilter(GatewayCorsProperties corsProperties) {
        CorsConfiguration corsConfiguration = new CorsConfiguration();
        corsConfiguration.addAllowedOriginPattern(corsProperties.getAllowedOrigins());
        corsConfiguration.addAllowedHeader(CorsConfiguration.ALL);
        corsConfiguration.addAllowedMethod(CorsConfiguration.ALL);
        corsConfiguration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", corsConfiguration);
        return new CorsWebFilter(source);
    }

    @Bean
    public GlobalFilter errorHandlingFilter() {
        return (exchange, chain) -> chain.filter(exchange).onErrorResume(ex -> handleError(exchange, ex));
    }

    private Mono<Void> handleError(ServerWebExchange exchange, Throwable ex) {
        LOGGER.error("Unhandled exception in gateway filter chain", ex);

        var response = exchange.getResponse();
        if (response.isCommitted()) {
            return Mono.error(ex);
        }

        response.setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = String.format("{\"error\":\"%s\"}", ex.getMessage());
        return response.writeWith(Mono.just(response.bufferFactory().wrap(body.getBytes())));
    }
}
