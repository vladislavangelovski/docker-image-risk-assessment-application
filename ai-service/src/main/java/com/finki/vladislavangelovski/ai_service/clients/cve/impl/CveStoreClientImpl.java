package com.finki.vladislavangelovski.ai_service.clients.cve.impl;

import com.finki.vladislavangelovski.ai_service.clients.cve.CveStoreClient;
import com.finki.vladislavangelovski.common.dto.CveEntryDto;
import com.finki.vladislavangelovski.common.dto.CveForEmbedding;
import com.finki.vladislavangelovski.common.dto.EpssScoreDto;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.math.BigDecimal;
import java.util.*;

@Component
public class CveStoreClientImpl implements CveStoreClient {
    
    private final WebClient cveWebClient;
    private final String byIdPath;
    private final String epssPath;
    private final String listPath;
    
    public CveStoreClientImpl(@Qualifier("cveStoreWebClient") WebClient cveStoreWebClient,
                              @Value("${services.cvestore.by-id-path}") String byIdPath,
                              @Value("${services.cvestore.epss-path}") String epssPath,
                              @Value("${services.cvestore.list-path:}") String configuredListPath) {
        this.cveWebClient = cveStoreWebClient;
        this.byIdPath = byIdPath;
        this.epssPath = epssPath;
        this.listPath = resolveListPath(byIdPath, configuredListPath);
    }
    
    // --------------------- helpers ---------------------
    
    private static Double toDouble(BigDecimal bd) {
        return (bd != null) ? bd.doubleValue() : null;
    }
    
    /**
     * Central place that converts CVE + optional EPSS into CveForEmbedding.
     * EPSS from EpssScoreDto (latest) has priority; if null, we fall back to
     * the flattened epssScore/epssPercentile on CveEntryDto (if present).
     */
    private CveForEmbedding toEmbedding(CveEntryDto base, EpssScoreDto latestEpss) {
        if (base == null) {
            return null;
        }
        
        String title = base.getCveId();              // we don't have a separate title field, so use CVE ID
        String description = base.getDescription();
        var cwe = base.getWeaknesses();              // List<String>
        Double cvssBase = toDouble(base.getCvssBaseScore());
        String cvssVector = base.getCvssVector();
        var published = base.getPublishedDate();
        var lastModified = base.getLastModified();
        var references = base.getReferences();       // List<Reference>
        
        Double epss = null;
        Double epssPercentile = null;
        
        if (latestEpss != null) {
            epss = toDouble(latestEpss.getScore());
            epssPercentile = toDouble(latestEpss.getPercentile());
        } else {
            epss = toDouble(base.getEpssScore());
            epssPercentile = toDouble(base.getEpssPercentile());
        }
        
        return new CveForEmbedding(
                base.getCveId(),
                title,
                description,
                cwe,
                cvssBase,
                cvssVector,
                published,
                lastModified,
                references,
                epss,
                epssPercentile
        );
    }
    
    private Optional<EpssScoreDto> fetchLatestEpss(String cveId) {
        try {
            var list = cveWebClient.get()
                    .uri(uri -> uri.path(epssPath).queryParam("limit", 1).build(cveId))
                    .retrieve()
                    .bodyToMono(new org.springframework.core.ParameterizedTypeReference<List<EpssScoreDto>>() {})
                    .block();
            
            return (list != null && !list.isEmpty())
                    ? Optional.ofNullable(list.get(0))
                    : Optional.empty();
            
        } catch (WebClientResponseException.NotFound ex) {
            return Optional.empty();
        }
    }
    
    // --------------------- interface methods ---------------------
    
    @Override
    public CveForEmbedding getById(String cveId) {
        var base = cveWebClient.get()
                .uri(byIdPath, cveId)
                .retrieve()
                .bodyToMono(CveEntryDto.class)
                .block();
        
        if (base == null) {
            return null;
        }
        
        var epss = fetchLatestEpss(cveId).orElse(null);
        return toEmbedding(base, epss);
    }
    
    @Override
    public Map<String, CveForEmbedding> getByIds(List<String> cveIds) {
        var map = new LinkedHashMap<String, CveForEmbedding>();
        if (cveIds == null || cveIds.isEmpty()) {
            return map;
        }
        
        for (var id : cveIds) {
            try {
                var cve = getById(id);
                if (cve != null) {
                    map.put(id, cve);
                }
            } catch (Exception ignored) {
                // swallow individual CVE failures, keep going
            }
        }
        return map;
    }
    
    @Override
    public List<CveForEmbedding> findCandidatesForEmbedding(int limit) {
        int size = (limit <= 0) ? 100 : Math.min(limit, 100); // cve-store caps size at 100
        return findCandidatesForEmbeddingPage(0, size);
    }
    
    @Override
    public List<CveForEmbedding> findCandidatesForEmbeddingPage(int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = (size <= 0) ? 100 : Math.min(size, 100); // cve-store caps size at 100
        
        CvePageResponse response = cveWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path(listPath)
                        .queryParam("page", safePage)
                        .queryParam("size", safeSize)
                        .build())
                .retrieve()
                .bodyToMono(CvePageResponse.class)
                .block();
        
        if (response == null || response.content == null || response.content.isEmpty()) {
            return List.of();
        }
        
        List<CveForEmbedding> result = new ArrayList<>(response.content.size());
        for (CveEntryDto e : response.content) {
            if (e == null || e.getCveId() == null || e.getCveId().isBlank()) {
                continue;
            }
            // Use flattened EPSS from CveEntryDto if present (cve-store joins latest EPSS)
            var mapped = toEmbedding(e, null);
            if (mapped != null) {
                result.add(mapped);
            }
        }
        
        return result;
    }
    
    private static String resolveListPath(String byIdPath, String configuredListPath) {
        if (configuredListPath != null && !configuredListPath.isBlank()) {
            return configuredListPath;
        }
        
        if (byIdPath == null || byIdPath.isBlank()) {
            return "";
        }
        
        if (byIdPath.contains("/{cveId}")) {
            return byIdPath.replace("/{cveId}", "");
        }
        
        int placeholder = byIdPath.indexOf("/{");
        return (placeholder > 0) ? byIdPath.substring(0, placeholder) : byIdPath;
    }
    
    private static final class CvePageResponse {
        public List<CveEntryDto> content;
    }
}
