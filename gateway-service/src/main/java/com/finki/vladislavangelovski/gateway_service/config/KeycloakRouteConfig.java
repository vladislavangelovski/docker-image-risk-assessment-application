package com.finki.vladislavangelovski.gateway_service.config;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class KeycloakRouteConfig {

  @Bean
  RouteLocator keycloakRouteLocator(
      RouteLocatorBuilder builder, ServiceEndpointsProperties properties) {
    return builder
        .routes()
        .route(
            "keycloak",
            r ->
                r.path("/auth", "/auth/**")
                    .filters(filters -> filters.removeResponseHeader("X-Frame-Options"))
                    .uri(properties.getKeycloakBaseUrl()))
        .build();
  }
}
