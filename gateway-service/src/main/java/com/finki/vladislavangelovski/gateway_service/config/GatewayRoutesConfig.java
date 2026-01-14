package com.finki.vladislavangelovski.gateway_service.config;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@Configuration
@ConditionalOnProperty(prefix = "gateway.routes", name = "source", havingValue = "java", matchIfMissing = false)
public class GatewayRoutesConfig {

    @Bean
    public RouteLocator routeLocator(
            RouteLocatorBuilder builder,
            ServiceEndpointsProperties properties) {
        return builder
                .routes()
                .route(
                        "scan-service",
                        r ->
                                r.path("/api/v1/scans/**")
                                        .uri(properties.getScanBaseUrl()))
                .route(
                        "cve-store-service",
                        r ->
                                r.path("/api/v1/cves/**")
                                        .uri(properties.getCveStoreBaseUrl()))
                .route(
                        "ai-service",
                        r ->
                                r.path("/api/v1/ai/**")
                                        .uri(properties.getAiBaseUrl()))
                .build();
    }
}
