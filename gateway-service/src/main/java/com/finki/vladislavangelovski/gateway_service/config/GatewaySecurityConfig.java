package com.finki.vladislavangelovski.gateway_service.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finki.vladislavangelovski.common.error.ErrorResponse;
import com.finki.vladislavangelovski.gateway_service.filter.RequestIdFilter;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Configuration
@EnableWebFluxSecurity
public class GatewaySecurityConfig {
  private final GatewaySecurityProperties properties;
  private final ObjectMapper objectMapper;

  public GatewaySecurityConfig(GatewaySecurityProperties properties, ObjectMapper objectMapper) {
    this.properties = properties;
    this.objectMapper = objectMapper;
  }

  @org.springframework.context.annotation.Bean
  public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
    http.csrf(ServerHttpSecurity.CsrfSpec::disable);
    http.httpBasic(ServerHttpSecurity.HttpBasicSpec::disable);
    http.formLogin(ServerHttpSecurity.FormLoginSpec::disable);
    http.logout(ServerHttpSecurity.LogoutSpec::disable);
    http.headers(
        headers -> headers.frameOptions(ServerHttpSecurity.HeaderSpec.FrameOptionsSpec::disable));

    if (!properties.isEnabled()) {
      return http.authorizeExchange(exchanges -> exchanges.anyExchange().permitAll()).build();
    }

    return http.authorizeExchange(
            exchanges ->
                exchanges
                    .pathMatchers(HttpMethod.OPTIONS, "/**")
                    .permitAll()
                    .pathMatchers("/auth/**")
                    .permitAll()
                    .pathMatchers("/actuator/health/**", "/health")
                    .permitAll()
                    .pathMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**")
                    .permitAll()
                    .pathMatchers("/api/v1/admin/**", "/api/admin/**")
                    .hasAnyAuthority("SCOPE_admin", "ROLE_ADMIN")
                    .pathMatchers("/api/**")
                    .authenticated()
                    .anyExchange()
                    .denyAll())
        .oauth2ResourceServer(
            oauth2 ->
                oauth2.jwt(
                    jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverterAdapter())))
        .exceptionHandling(
            exceptions ->
                exceptions
                    .authenticationEntryPoint(authenticationEntryPoint())
                    .accessDeniedHandler(accessDeniedHandler()))
        .build();
  }

  private ReactiveJwtAuthenticationConverterAdapter jwtAuthenticationConverterAdapter() {
    JwtGrantedAuthoritiesConverter scopeAuthorities = new JwtGrantedAuthoritiesConverter();
    JwtAuthenticationConverter jwtAuthenticationConverter = new JwtAuthenticationConverter();
    jwtAuthenticationConverter.setJwtGrantedAuthoritiesConverter(
        jwt -> {
          Set<GrantedAuthority> authorities = new LinkedHashSet<>(scopeAuthorities.convert(jwt));
          authorities.addAll(extractRoles(jwt));
          return authorities;
        });
    return new ReactiveJwtAuthenticationConverterAdapter(jwtAuthenticationConverter);
  }

  private static Collection<? extends GrantedAuthority> extractRoles(Jwt jwt) {
    Set<GrantedAuthority> out = new LinkedHashSet<>();
    if (jwt == null) {
      return out;
    }

    Object direct = jwt.getClaims().get("roles");
    addRoleValues(out, direct);

    Object realmAccess = jwt.getClaims().get("realm_access");
    if (realmAccess instanceof Map<?, ?> map) {
      addRoleValues(out, map.get("roles"));
    }

    return out;
  }

  private static void addRoleValues(Set<GrantedAuthority> out, Object value) {
    if (value instanceof String s) {
      for (String part : s.split("[,\\s]+")) {
        addRole(out, part);
      }
      return;
    }
    if (value instanceof Collection<?> coll) {
      for (Object v : coll) {
        if (v != null) {
          addRole(out, v.toString());
        }
      }
    }
  }

  private static void addRole(Set<GrantedAuthority> out, String role) {
    if (role == null) {
      return;
    }
    String trimmed = role.trim();
    if (trimmed.isEmpty()) {
      return;
    }
    String normalized = trimmed.startsWith("ROLE_") ? trimmed.substring("ROLE_".length()) : trimmed;
    out.add(new SimpleGrantedAuthority("ROLE_" + normalized.toUpperCase()));
  }

  private ServerAuthenticationEntryPoint authenticationEntryPoint() {
    return (exchange, ex) -> writeError(exchange, HttpStatus.UNAUTHORIZED, "Unauthorized");
  }

  private ServerAccessDeniedHandler accessDeniedHandler() {
    return (exchange, ex) -> writeError(exchange, HttpStatus.FORBIDDEN, "Forbidden");
  }

  private Mono<Void> writeError(ServerWebExchange exchange, HttpStatus status, String message) {
    var response = exchange.getResponse();
    if (response.isCommitted()) {
      return Mono.empty();
    }

    response.setStatusCode(status);
    response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

    String requestId = exchange.getRequest().getHeaders().getFirst(RequestIdFilter.HEADER);
    if (requestId == null || requestId.isBlank()) {
      requestId = exchange.getRequest().getId();
    }

    ErrorResponse body =
        ErrorResponse.of(
            status.value(), message, exchange.getRequest().getPath().value(), requestId, Map.of());

    byte[] bytes;
    try {
      bytes = objectMapper.writeValueAsBytes(body);
    } catch (Exception jsonException) {
      String fallback = "{\"status\":%d,\"message\":\"%s\"}".formatted(status.value(), message);
      bytes = fallback.getBytes(StandardCharsets.UTF_8);
    }
    return response.writeWith(Mono.just(response.bufferFactory().wrap(bytes)));
  }
}
