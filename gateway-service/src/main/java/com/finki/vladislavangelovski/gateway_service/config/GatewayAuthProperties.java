package com.finki.vladislavangelovski.gateway_service.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "gateway.auth")
public class GatewayAuthProperties {
    private boolean enabled = false;
    private String apiKey = "";
    private String header = "X-API-Key";
    private List<String> excludedPaths = List.of("/actuator", "/health", "/swagger-ui", "/v3/api-docs");

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getHeader() {
        return header;
    }

    public void setHeader(String header) {
        this.header = header;
    }

    public List<String> getExcludedPaths() {
        return excludedPaths;
    }

    public void setExcludedPaths(List<String> excludedPaths) {
        this.excludedPaths = excludedPaths;
    }
}
