package com.finki.vladislavangelovski.gateway_service.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class UserContextFilter implements GlobalFilter, Ordered {
  public static final String USER_ID_HEADER = "X-User-Id";
  public static final String USER_NAME_HEADER = "X-User-Name";
  public static final String USER_EMAIL_HEADER = "X-User-Email";

  @Override
  public int getOrder() {
    return -4;
  }

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    return exchange
        .getPrincipal()
        .ofType(Authentication.class)
        .flatMap(
            auth -> {
              String userId = null;
              String userName = null;
              String userEmail = null;

              if (auth instanceof JwtAuthenticationToken jwtAuth) {
                Jwt jwt = jwtAuth.getToken();
                userId = jwt.getSubject();
                userName =
                    firstNonBlank(
                        jwt.getClaimAsString("preferred_username"), jwt.getClaimAsString("name"));
                userEmail = jwt.getClaimAsString("email");
              }

              if (!StringUtils.hasText(userId)) {
                userId = auth.getName();
              }

              ServerHttpRequest mutated =
                  withUserHeaders(exchange.getRequest(), userId, userName, userEmail);
              return chain.filter(exchange.mutate().request(mutated).build());
            })
        .switchIfEmpty(
            chain.filter(
                exchange
                    .mutate()
                    .request(withUserHeaders(exchange.getRequest(), null, null, null))
                    .build()));
  }

  private static ServerHttpRequest withUserHeaders(
      ServerHttpRequest request, String userId, String userName, String userEmail) {
    return request
        .mutate()
        .headers(
            headers -> {
              headers.remove(USER_ID_HEADER);
              headers.remove(USER_NAME_HEADER);
              headers.remove(USER_EMAIL_HEADER);
              if (StringUtils.hasText(userId)) {
                headers.add(USER_ID_HEADER, userId);
              }
              if (StringUtils.hasText(userName)) {
                headers.add(USER_NAME_HEADER, userName);
              }
              if (StringUtils.hasText(userEmail)) {
                headers.add(USER_EMAIL_HEADER, userEmail);
              }
            })
        .build();
  }

  private static String firstNonBlank(String first, String second) {
    if (StringUtils.hasText(first)) {
      return first;
    }
    if (StringUtils.hasText(second)) {
      return second;
    }
    return null;
  }
}
