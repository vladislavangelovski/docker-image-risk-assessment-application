package com.finki.vladislavangelovski.gateway_service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;

@SpringBootTest
class GatewayRouteContractTest {
  @Autowired RouteDefinitionLocator locator;

  @Test
  void exposesGatewayServiceContracts() {
    List<String> ids =
        locator.getRouteDefinitions().map(RouteDefinition::getId).collectList().block();
    assertThat(ids)
        .contains(
            "keycloak",
            "scan-service",
            "cve-store-service",
            "ai-service-assess",
            "ai-service-qa",
            "ai-service-admin-embeddings",
            "ai-service-semantic");
  }
}
