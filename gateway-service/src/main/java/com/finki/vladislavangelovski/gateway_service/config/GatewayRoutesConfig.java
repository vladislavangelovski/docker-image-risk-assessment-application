package com.finki.vladislavangelovski.gateway_service.config;

import com.finki.vladislavangelovski.gateway_service.filter.AuthenticationPlaceholderFilter;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GatewayRoutesConfig {

    @Bean
    public RouteLocator routeLocator(
            RouteLocatorBuilder builder,
            ServiceEndpointsProperties properties,
            AuthenticationPlaceholderFilter authenticationPlaceholderFilter) {
        return builder
                .routes()
                .route(
                        "scan-service",
                        r ->
                                r.path("/api/v1/scans/**")
                                        .filters(
                                                f ->
                                                        f.filter(authenticationPlaceholderFilter.apply(new AuthenticationPlaceholderFilter.Config()))
                                                                .preserveHostHeader())
                                        .uri(properties.getScanBaseUrl()))
                .route(
                        "cve-store-service",
                        r ->
                                r.path("/api/v1/cves/**")
                                        .filters(
                                                f ->
                                                        f.filter(authenticationPlaceholderFilter.apply(new AuthenticationPlaceholderFilter.Config()))
                                                                .preserveHostHeader())
                                        .uri(properties.getCveStoreBaseUrl()))
                .route(
                        "ai-service",
                        r ->
                                r.path("/api/v1/ai/**")
                                        .filters(
                                                f ->
                                                        f.filter(authenticationPlaceholderFilter.apply(new AuthenticationPlaceholderFilter.Config()))
                                                                .preserveHostHeader())
                                        .uri(properties.getAiBaseUrl()))
                .build();
    }
}
