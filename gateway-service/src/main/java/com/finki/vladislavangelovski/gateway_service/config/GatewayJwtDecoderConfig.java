package com.finki.vladislavangelovski.gateway_service.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.security.oauth2.resource.OAuth2ResourceServerProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.util.StringUtils;

@Configuration
@ConditionalOnProperty(prefix = "gateway.security", name = "enabled", havingValue = "true")
public class GatewayJwtDecoderConfig {

  @Bean
  ReactiveJwtDecoder reactiveJwtDecoder(OAuth2ResourceServerProperties properties) {
    OAuth2ResourceServerProperties.Jwt jwt = properties.getJwt();
    String jwkSetUri = jwt.getJwkSetUri();
    if (!StringUtils.hasText(jwkSetUri)) {
      throw new IllegalStateException(
          "Missing required property: spring.security.oauth2.resourceserver.jwt.jwk-set-uri");
    }

    NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder.withJwkSetUri(jwkSetUri).build();

    String expectedIssuer = jwt.getIssuerUri();
    OAuth2TokenValidator<Jwt> validator =
        StringUtils.hasText(expectedIssuer)
            ? JwtValidators.createDefaultWithIssuer(expectedIssuer)
            : JwtValidators.createDefault();
    decoder.setJwtValidator(validator);
    return decoder;
  }
}

