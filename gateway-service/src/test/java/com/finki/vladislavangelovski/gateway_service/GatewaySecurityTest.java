package com.finki.vladislavangelovski.gateway_service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "gateway.security.enabled=true",
      "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:9999/jwks",
      "services.scan-base-url=http://127.0.0.1:1",
      "services.cve-store-base-url=http://127.0.0.1:1",
      "services.ai-base-url=http://127.0.0.1:1",
      "services.keycloak-base-url=http://127.0.0.1:1"
    })
@AutoConfigureWebTestClient
class GatewaySecurityTest {

  @Autowired WebTestClient webTestClient;

  @Test
  void rejectsUnauthenticatedApiRequests() {
    webTestClient
        .get()
        .uri("/api/v1/_test/ping")
        .exchange()
        .expectStatus()
        .isUnauthorized()
        .expectHeader()
        .contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
        .expectBody()
        .jsonPath("$.status")
        .isEqualTo(401)
        .jsonPath("$.message")
        .isEqualTo("Unauthorized");
  }

  @Test
  void rejectsNonAdminAccessToAdminEndpoints() {
    webTestClient
        .mutateWith(mockJwt().authorities(new SimpleGrantedAuthority("ROLE_USER")))
        .get()
        .uri("/api/v1/admin/_test/ping")
        .exchange()
        .expectStatus()
        .isForbidden()
        .expectHeader()
        .contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
        .expectBody()
        .jsonPath("$.status")
        .isEqualTo(403)
        .jsonPath("$.message")
        .isEqualTo("Forbidden");
  }

  @Test
  void allowsAdminAccessToAdminEndpoints() {
    webTestClient
        .mutateWith(mockJwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
        .get()
        .uri("/api/v1/admin/_test/ping")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(String.class)
        .value(body -> assertThat(body).isEqualTo("ok"));
  }

  @Test
  void allowsAuthenticatedUsersToCallApiRoutes() {
    webTestClient
        .mutateWith(mockJwt().authorities(new SimpleGrantedAuthority("ROLE_USER")))
        .get()
        .uri("/api/v1/_test/ping")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(String.class)
        .value(body -> assertThat(body).isEqualTo("ok"));
  }

  @Test
  void doesNotProtectActuatorHealth() {
    webTestClient
        .get()
        .uri("/actuator/health")
        .exchange()
        .expectStatus()
        .value(status -> assertThat(status).isNotIn(401, 403));
  }
}
