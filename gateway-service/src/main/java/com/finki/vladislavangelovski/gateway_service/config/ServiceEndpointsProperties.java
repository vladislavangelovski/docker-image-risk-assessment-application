package com.finki.vladislavangelovski.gateway_service.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "services")
public class ServiceEndpointsProperties {

  private String scanBaseUrl = "http://scan-service:8080";
  private String cveStoreBaseUrl = "http://cve-store:8080";
  private String aiBaseUrl = "http://ai-service:8083";
  private String keycloakBaseUrl = "http://keycloak:8080";

  public String getScanBaseUrl() {
    return scanBaseUrl;
  }

  public void setScanBaseUrl(String scanBaseUrl) {
    this.scanBaseUrl = scanBaseUrl;
  }

  public String getCveStoreBaseUrl() {
    return cveStoreBaseUrl;
  }

  public void setCveStoreBaseUrl(String cveStoreBaseUrl) {
    this.cveStoreBaseUrl = cveStoreBaseUrl;
  }

  public String getAiBaseUrl() {
    return aiBaseUrl;
  }

  public void setAiBaseUrl(String aiBaseUrl) {
    this.aiBaseUrl = aiBaseUrl;
  }

  public String getKeycloakBaseUrl() {
    return keycloakBaseUrl;
  }

  public void setKeycloakBaseUrl(String keycloakBaseUrl) {
    this.keycloakBaseUrl = keycloakBaseUrl;
  }
}
