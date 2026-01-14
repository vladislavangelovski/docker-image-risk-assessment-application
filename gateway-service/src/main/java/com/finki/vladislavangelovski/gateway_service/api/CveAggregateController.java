package com.finki.vladislavangelovski.gateway_service.api;

import com.finki.vladislavangelovski.common.dto.CveEntryDto;
import com.finki.vladislavangelovski.common.dto.EpssScoreDto;
import com.finki.vladislavangelovski.gateway_service.config.ServiceEndpointsProperties;
import com.finki.vladislavangelovski.gateway_service.filter.RequestIdFilter;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
public class CveAggregateController {
    private final WebClient webClient;
    private final ServiceEndpointsProperties properties;

    public CveAggregateController(WebClient webClient,
                                  ServiceEndpointsProperties properties) {
        this.webClient = webClient;
        this.properties = properties;
    }

    @GetMapping("/api/v1/aggregate/cves/{id}")
    public Mono<CveAggregateResponse> getCveWithEpss(@PathVariable("id") String id,
                                                     @RequestHeader(name = RequestIdFilter.HEADER, required = false)
                                                     String requestId) {
        String baseUrl = properties.getCveStoreBaseUrl();
        Mono<CveEntryDto> cve = webClient.get()
                .uri(baseUrl + "/api/v1/cves/{id}", id)
                .headers(headers -> addRequestId(headers, requestId))
                .retrieve()
                .bodyToMono(CveEntryDto.class);

        Mono<List<EpssScoreDto>> epss = webClient.get()
                .uri(baseUrl + "/api/v1/cves/{id}/epss?limit=1", id)
                .headers(headers -> addRequestId(headers, requestId))
                .retrieve()
                .bodyToFlux(EpssScoreDto.class)
                .collectList();

        return Mono.zip(cve, epss)
                .map(tuple -> new CveAggregateResponse(tuple.getT1(), tuple.getT2()));
    }

    private static void addRequestId(org.springframework.http.HttpHeaders headers,
                                     String requestId) {
        if (requestId != null && !requestId.isBlank()) {
            headers.set(RequestIdFilter.HEADER, requestId);
        }
    }

    public record CveAggregateResponse(CveEntryDto cve,
                                       List<EpssScoreDto> epssScores) {
    }
}
