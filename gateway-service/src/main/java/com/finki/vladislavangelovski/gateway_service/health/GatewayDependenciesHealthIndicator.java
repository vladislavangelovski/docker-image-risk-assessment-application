package com.finki.vladislavangelovski.gateway_service.health;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.ReactiveHealthIndicator;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class GatewayDependenciesHealthIndicator implements ReactiveHealthIndicator {

    private final DependencyHealthService dependencyHealthService;

    public GatewayDependenciesHealthIndicator(DependencyHealthService dependencyHealthService) {
        this.dependencyHealthService = dependencyHealthService;
    }

    @Override
    public Mono<Health> health() {
        return dependencyHealthService.checkDependencies()
                .map(summary ->
                        Health.status(summary.status())
                                .withDetails(summary.dependencies())
                                .build());
    }
}
