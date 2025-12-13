package com.finki.vladislavangelovski.ai_service.clients.scan;

import com.finki.vladislavangelovski.ai_service.clients.scan.dto.ScanClient;
import com.finki.vladislavangelovski.ai_service.clients.scan.dto.ScanResult;
import com.finki.vladislavangelovski.ai_service.clients.scan.exception.ScanClientException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.util.Map;

@Component
public class ScanClientImpl implements ScanClient {
    private static final Logger log = LoggerFactory.getLogger(ScanClientImpl.class);
    private final WebClient scanWebClient;
    private final String assessPath;
    
    public ScanClientImpl(@Qualifier("scanWebClient") WebClient scanWebClient,
                          @Value("${services.scan.assess-path}") String assessPath) {
        this.scanWebClient = scanWebClient;
        this.assessPath = assessPath;
    }
    
    @Override
    public ScanResult scanImage(String imageRef) {
        try {
            return scanWebClient.post()
                    .uri(assessPath)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(Map.of("image", imageRef))
                    .retrieve()
                    .onStatus(status -> status.isError(), resp -> resp.createException().flatMap(Mono::error))
                    .bodyToMono(ScanResult.class)
                    .block();
        } catch (WebClientResponseException ex) {
            log.error("[ai-service] Scan submission failed with status {} and body {}", ex.getStatusCode(),
                    ex.getResponseBodyAsString(), ex);
            throw new ScanClientException("Scan submission failed: HTTP " + ex.getStatusCode().value() +
                    " " + ex.getStatusText(), ex);
        } catch (Exception ex) {
            log.error("[ai-service] Scan submission failed for image {}", imageRef, ex);
            throw new ScanClientException("Scan submission failed: " + ex.getMessage(), ex);
        }
    }
}
