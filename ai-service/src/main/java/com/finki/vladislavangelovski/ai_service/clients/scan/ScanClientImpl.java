package com.finki.vladislavangelovski.ai_service.clients.scan;

import com.finki.vladislavangelovski.ai_service.clients.scan.dto.ScanClient;
import com.finki.vladislavangelovski.ai_service.clients.scan.dto.ScanResult;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

@Component
public class ScanClientImpl implements ScanClient {
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
                    .bodyToMono(ScanResult.class)
                    .block();
        } catch (Exception postFailed) {
            return scanWebClient.get()
                    .uri(uri -> uri.path(assessPath).queryParam("imageRef", imageRef).build())
                    .retrieve()
                    .bodyToMono(ScanResult.class)
                    .block();
        }
    }
}
