package com.finki.vladislavangelovski.ai_service.clients.cve.impl;

import com.finki.vladislavangelovski.ai_service.clients.cve.CveStoreClient;
import com.finki.vladislavangelovski.common.dto.CveForEmbedding;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class CveStoreClientImpl implements CveStoreClient {
    private final WebClient cveWebClient;
    private final String byIdPath;
    private final String epssPath;

    public CveStoreClientImpl(
            @Qualifier("cveStoreWebClient") WebClient cveStoreWebClient,
            @Value("${services.cvestore.by-id-path}") String byIdPath,
            @Value("${services.cvestore.epss-path}") String epssPath) {
        this.cveWebClient = cveStoreWebClient;
        this.byIdPath = byIdPath;
        this.epssPath = epssPath;
    }

    static record EpssScoreDto(Double score, Double percentile) {}

    private Optional<EpssScoreDto> fetchLatestEpss(String cveId) {
        var list = cveWebClient.get()
                .uri(uri -> uri.path(epssPath).queryParam("limit", 1).build(cveId))
                .retrieve()
                .bodyToMono(new org.springframework.core.ParameterizedTypeReference<java.util.List<EpssScoreDto>>() {})
                .block();
        return (list != null && !list.isEmpty()) ? Optional.ofNullable(list.get(0)) : Optional.empty();
    }

    @Override
    public CveForEmbedding getById(String cveId) {
        var base = cveWebClient.get()
                .uri(byIdPath, cveId)
                .retrieve()
                .bodyToMono(CveForEmbedding.class)
                .block();

        if (base == null) return null;

        var epss = fetchLatestEpss(cveId);
        if (epss.isEmpty()) return base;

        var e = epss.get();
        return new CveForEmbedding(
                base.cveId(),
                base.title(),
                base.description(),
                base.cwe(),
                base.cvssBase(),
                base.cvssVector(),
                base.published(),
                base.lastModified(),
                base.references(),
                e.score(),
                e.percentile()
        );
    }

    @Override
    public Map<String, CveForEmbedding> getByIds(java.util.List<String> cveIds) {
        var map = new java.util.LinkedHashMap<String, CveForEmbedding>();
        for (var id : cveIds) {
            try {
                var cve = getById(id);
                if (cve != null) map.put(id, cve);
            } catch (Exception ignored) {}
        }
        return map;
    }
}
