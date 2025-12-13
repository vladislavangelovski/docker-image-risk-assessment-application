package com.finki.vladislavangelovski.gateway_service.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.stereotype.Component;

@Component
public class AuthenticationPlaceholderFilter
        extends AbstractGatewayFilterFactory<AuthenticationPlaceholderFilter.Config> {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthenticationPlaceholderFilter.class);

    public AuthenticationPlaceholderFilter() {
        super(Config.class);
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            LOGGER.debug("Authentication placeholder filter invoked for {}", exchange.getRequest().getURI());
            // TODO: Implement API key or JWT validation when requirements are finalized.
            return chain.filter(exchange);
        };
    }

    public static class Config {
        private String requiredScope;

        public String getRequiredScope() {
            return requiredScope;
        }

        public void setRequiredScope(String requiredScope) {
            this.requiredScope = requiredScope;
        }
    }
}
