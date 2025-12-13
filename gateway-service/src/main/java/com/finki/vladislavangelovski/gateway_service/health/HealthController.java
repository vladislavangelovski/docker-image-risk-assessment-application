package com.finki.vladislavangelovski.gateway_service.health;

import com.finki.vladislavangelovski.gateway_service.config.ServiceEndpointsProperties;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@RestController
public class HealthController {

    private static final Logger LOGGER = LoggerFactory.getLogger(HealthController.class);

    private final WebClient webClient;
    private final ServiceEndpointsProperties properties;

    public HealthController(WebClient webClient, ServiceEndpointsProperties properties) {
        this.webClient = webClient;
        this.properties = properties;
    }

    @GetMapping("/health")
    public Mono<ResponseEntity<HealthStatus>> health() {
        Mono<DependencyHealth> scanHealth = checkHealth("scan-service", properties.getScanBaseUrl());
        Mono<DependencyHealth> cveStoreHealth = checkHealth("cve-store-service", properties.getCveStoreBaseUrl());
        Mono<DependencyHealth> aiHealth = checkHealth("ai-service", properties.getAiBaseUrl());

        return Mono.zip(scanHealth, cveStoreHealth, aiHealth)
                .map(tuple -> {
                    Map<String, DependencyHealth> dependencies = new LinkedHashMap<>();
                    dependencies.put(tuple.getT1().name(), tuple.getT1());
                    dependencies.put(tuple.getT2().name(), tuple.getT2());
                    dependencies.put(tuple.getT3().name(), tuple.getT3());

                    String status = dependencies.values().stream().allMatch(DependencyHealth::isHealthy)
                            ? "UP"
                            : "DEGRADED";

                    HttpStatus httpStatus = status.equals("UP") ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE;
                    return ResponseEntity.status(httpStatus).body(new HealthStatus(status, dependencies));
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
                            return Mono.just(new DependencyHealth(name, "DOWN"));
                        });
    }

    private record HealthResponse(String status) {}

    public record DependencyHealth(String name, String status) {
        public boolean isHealthy() {
            return "UP".equalsIgnoreCase(status);
        }
    }

    public record HealthStatus(String status, Map<String, DependencyHealth> dependencies) {}
}
