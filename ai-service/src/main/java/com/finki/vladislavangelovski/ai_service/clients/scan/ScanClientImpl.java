package com.finki.vladislavangelovski.ai_service.clients.scan;

import com.finki.vladislavangelovski.ai_service.clients.scan.dto.ConfigScanResult;
import com.finki.vladislavangelovski.ai_service.clients.scan.dto.ScanClient;
import com.finki.vladislavangelovski.ai_service.clients.scan.dto.ScanResult;
import com.finki.vladislavangelovski.ai_service.clients.scan.exception.ScanClientException;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

@Component
public class ScanClientImpl implements ScanClient {
  private static final Logger log = LoggerFactory.getLogger(ScanClientImpl.class);
  private final WebClient scanWebClient;
  private final String assessPath;

  public ScanClientImpl(
      @Qualifier("scanWebClient") WebClient scanWebClient,
      @Value("${services.scan.assess-path}") String assessPath) {
    this.scanWebClient = scanWebClient;
    this.assessPath = assessPath;
  }

  @Override
  public ScanResult scanImage(String imageRef) {
    try {
      Optional<ScanResult> cached = fetchExisting(imageRef);
      if (cached.isPresent()) {
        return cached.get();
      }
    } catch (ScanClientException e) {
      log.warn(
          "[ai-service] Existing scan lookup failed for image {}; proceeding with submission",
          imageRef,
          e);
    }

    return submitScan(imageRef);
  }

  @Override
  public ConfigScanResult scanDockerCompose(String composeYaml) {
    return submitConfigScan("/config/docker-compose", composeYaml);
  }

  @Override
  public ConfigScanResult scanDockerfile(String dockerfile) {
    return submitConfigScan("/config/dockerfile", dockerfile);
  }

  private Optional<ScanResult> fetchExisting(String imageRef) {
    try {
      ScanResult result =
          scanWebClient
              .get()
              .uri(
                  uriBuilder ->
                      uriBuilder.path(assessPath).queryParam("imageRef", imageRef).build())
              .retrieve()
              .onStatus(
                  status -> status.isError(), resp -> resp.createException().flatMap(Mono::error))
              .bodyToMono(ScanResult.class)
              .block();
      return Optional.ofNullable(result);
    } catch (WebClientResponseException.NotFound ex) {
      log.info("[ai-service] No cached scan found for image {}", imageRef);
      return Optional.empty();
    } catch (WebClientResponseException ex) {
      log.warn(
          "[ai-service] Scan lookup failed with status {} and body {}",
          ex.getStatusCode(),
          ex.getResponseBodyAsString(),
          ex);
      throw new ScanClientException(
          "Scan lookup failed: HTTP " + ex.getStatusCode().value() + " " + ex.getStatusText(), ex);
    } catch (Exception ex) {
      log.warn("[ai-service] Scan lookup failed for image {}", imageRef, ex);
      throw new ScanClientException("Scan lookup failed: " + ex.getMessage(), ex);
    }
  }

  private ScanResult submitScan(String imageRef) {
    try {
      return scanWebClient
          .post()
          .uri(assessPath)
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(Map.of("image", imageRef))
          .retrieve()
          .onStatus(status -> status.isError(), resp -> resp.createException().flatMap(Mono::error))
          .bodyToMono(ScanResult.class)
          .block();
    } catch (WebClientResponseException ex) {
      log.error(
          "[ai-service] Scan submission failed with status {} and body {}",
          ex.getStatusCode(),
          ex.getResponseBodyAsString(),
          ex);
      throw new ScanClientException(
          "Scan submission failed: HTTP " + ex.getStatusCode().value() + " " + ex.getStatusText(),
          ex);
    } catch (Exception ex) {
      log.error("[ai-service] Scan submission failed for image {}", imageRef, ex);
      throw new ScanClientException("Scan submission failed: " + ex.getMessage(), ex);
    }
  }

  private ConfigScanResult submitConfigScan(String suffix, String content) {
    try {
      return scanWebClient
          .post()
          .uri(assessPath + suffix)
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(Map.of("content", content))
          .retrieve()
          .onStatus(status -> status.isError(), resp -> resp.createException().flatMap(Mono::error))
          .bodyToMono(ConfigScanResult.class)
          .block();
    } catch (WebClientResponseException ex) {
      log.error(
          "[ai-service] Config scan submission failed with status {} and body {}",
          ex.getStatusCode(),
          ex.getResponseBodyAsString(),
          ex);
      throw new ScanClientException(
          "Config scan submission failed: HTTP "
              + ex.getStatusCode().value()
              + " "
              + ex.getStatusText(),
          ex);
    } catch (Exception ex) {
      log.error("[ai-service] Config scan submission failed", ex);
      throw new ScanClientException("Config scan submission failed: " + ex.getMessage(), ex);
    }
  }
}
