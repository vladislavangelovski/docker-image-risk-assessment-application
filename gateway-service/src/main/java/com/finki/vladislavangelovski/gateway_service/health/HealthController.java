package com.finki.vladislavangelovski.gateway_service.health;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
public class HealthController {

  private final DependencyHealthService dependencyHealthService;

  public HealthController(DependencyHealthService dependencyHealthService) {
    this.dependencyHealthService = dependencyHealthService;
  }

  @GetMapping("/health")
  public Mono<ResponseEntity<HealthStatus>> health() {
    return dependencyHealthService
        .checkDependencies()
        .map(
            health -> {
              HttpStatus status =
                  health.status().equals(org.springframework.boot.actuate.health.Status.UP)
                      ? HttpStatus.OK
                      : HttpStatus.SERVICE_UNAVAILABLE;
              return ResponseEntity.status(status)
                  .body(new HealthStatus(health.status().getCode(), health.dependencies()));
            });
  }

  public record HealthStatus(
      String status,
      java.util.Map<String, DependencyHealthService.DependencyHealth> dependencies) {}
}
