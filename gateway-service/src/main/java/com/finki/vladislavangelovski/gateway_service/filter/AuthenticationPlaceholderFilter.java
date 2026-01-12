package com.finki.vladislavangelovski.gateway_service.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.PathMatcher;
import org.springframework.core.io.buffer.DataBuffer;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
public class AuthenticationPlaceholderFilter
        extends AbstractGatewayFilterFactory<AuthenticationPlaceholderFilter.Config> {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(AuthenticationPlaceholderFilter.class);
    
    private final PathMatcher pathMatcher = new AntPathMatcher();
    
    // Allow these without an API key (health checks +  swagger)
    private final List<String> allowPatterns = List.of(
            "/actuator/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/v3/api-docs/**"
    );
    
    @Value("${gateway.security.enabled:true}")
    private boolean enabled;
    
    @Value("${gateway.security.api-key:}")
    private String apiKey;
    
    @Value("${gateway.security.header:X-API-Key}")
    private String headerName;
    
    public AuthenticationPlaceholderFilter() {
        super(Config.class);
    }
    
    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            
            if (!enabled) {
                return chain.filter(exchange);
            }
            
            // If key is not configured, don’t block (dev-friendly).
            if (apiKey == null || apiKey.isBlank()) {
                LOGGER.warn("Gateway API key is NOT configured (gateway.security.api-key is empty). Authentication is effectively disabled.");
                return chain.filter(exchange);
            }
            
            // Don’t block CORS preflight
            if (exchange.getRequest().getMethod() == HttpMethod.OPTIONS) {
                return chain.filter(exchange);
            }
            
            String path = exchange.getRequest().getURI().getPath();
            if (isAllowed(path)) {
                return chain.filter(exchange);
            }
            
            String provided = exchange.getRequest().getHeaders().getFirst(headerName);
            if (provided != null && provided.equals(apiKey)) {
                return chain.filter(exchange);
            }
            
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
            
            byte[] bytes = "{\"error\":\"unauthorized\",\"message\":\"Missing or invalid API key\"}"
                    .getBytes(StandardCharsets.UTF_8);
            
            DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
            return exchange.getResponse().writeWith(Mono.just(buffer));
        };
    }
    
    private boolean isAllowed(String path) {
        for (String pattern : allowPatterns) {
            if (pathMatcher.match(pattern, path)) return true;
        }
        return false;
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
