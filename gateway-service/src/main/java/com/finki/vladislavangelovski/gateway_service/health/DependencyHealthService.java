package com.finki.vladislavangelovski.gateway_service.health;

import com.finki.vladislavangelovski.gateway_service.config.ServiceEndpointsProperties;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.health.Status;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Service
public class DependencyHealthService {

  private static final Logger LOGGER = LoggerFactory.getLogger(DependencyHealthService.class);

  private final WebClient webClient;
  private final ServiceEndpointsProperties properties;

  public DependencyHealthService(WebClient webClient, ServiceEndpointsProperties properties) {
    this.webClient = webClient;
    this.properties = properties;
  }

  public Mono<GatewayHealth> checkDependencies() {
    Mono<DependencyHealth> scanHealth = checkHealth("scan-service", properties.getScanBaseUrl());
    Mono<DependencyHealth> cveStoreHealth =
        checkHealth("cve-store-service", properties.getCveStoreBaseUrl());
    Mono<DependencyHealth> aiHealth = checkHealth("ai-service", properties.getAiBaseUrl());
    Mono<DependencyHealth> keycloakHealth = checkKeycloakHealth(properties.getKeycloakBaseUrl());

    return Mono.zip(scanHealth, cveStoreHealth, aiHealth, keycloakHealth)
        .map(
            tuple -> {
              Map<String, DependencyHealth> dependencies = new LinkedHashMap<>();
              dependencies.put(tuple.getT1().name(), tuple.getT1());
              dependencies.put(tuple.getT2().name(), tuple.getT2());
              dependencies.put(tuple.getT3().name(), tuple.getT3());
              dependencies.put(tuple.getT4().name(), tuple.getT4());

              boolean allHealthy =
                  dependencies.values().stream().allMatch(DependencyHealth::isHealthy);
              Status status = allHealthy ? Status.UP : new Status("DEGRADED");
              return new GatewayHealth(status, dependencies);
            });
  }

  private Mono<DependencyHealth> checkHealth(String name, String baseUrl) {
    String healthUrl = String.format("%s/actuator/health", baseUrl);
    return webClient
        .get()
        .uri(healthUrl)
        .retrieve()
        .bodyToMono(HealthResponse.class)
        .timeout(Duration.ofSeconds(5))
        .map(response -> new DependencyHealth(name, response.status()))
        .onErrorResume(
            ex -> {
              LOGGER.warn("Health check for {} failed: {}", name, ex.getMessage());
              return Mono.just(
                  new DependencyHealth(name, HttpStatus.SERVICE_UNAVAILABLE.getReasonPhrase()));
            });
  }

  private Mono<DependencyHealth> checkKeycloakHealth(String baseUrl) {
    String healthUrl =
        String.format("%s/auth/realms/risk/.well-known/openid-configuration", baseUrl);
    return webClient
        .get()
        .uri(healthUrl)
        .retrieve()
        .toBodilessEntity()
        .timeout(Duration.ofSeconds(5))
        .map(ignored -> new DependencyHealth("keycloak", Status.UP.getCode()))
        .onErrorResume(
            ex -> {
              LOGGER.warn("Health check for keycloak failed: {}", ex.getMessage());
              return Mono.just(
                  new DependencyHealth("keycloak", HttpStatus.SERVICE_UNAVAILABLE.getReasonPhrase()));
            });
  }

  private record HealthResponse(String status) {}

  public record DependencyHealth(String name, String status) {
    public boolean isHealthy() {
      return Status.UP.getCode().equalsIgnoreCase(status);
    }
  }

  public record GatewayHealth(Status status, Map<String, DependencyHealth> dependencies) {}
}
