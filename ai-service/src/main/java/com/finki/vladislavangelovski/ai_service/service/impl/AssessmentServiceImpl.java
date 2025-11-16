package com.finki.vladislavangelovski.ai_service.service.impl;

import com.finki.vladislavangelovski.ai_service.clients.cve.CveStoreClient;
import com.finki.vladislavangelovski.ai_service.clients.scan.dto.ScanClient;
import com.finki.vladislavangelovski.ai_service.clients.scan.dto.ScanFinding;
import com.finki.vladislavangelovski.ai_service.clients.scan.dto.ScanResult;
import com.finki.vladislavangelovski.ai_service.scoring.RiskScoring;
import com.finki.vladislavangelovski.ai_service.search.VectorSearchService;
import com.finki.vladislavangelovski.ai_service.search.dto.SearchHit;
import com.finki.vladislavangelovski.ai_service.service.AssessmentService;
import com.finki.vladislavangelovski.common.dto.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class AssessmentServiceImpl implements AssessmentService {
    
    private final ScanClient scanClient;
    private final CveStoreClient cveClient;
    
    private final int kDefault;
    private final int coverageCap;
    private final double wEpss;
    private final double wCvss;
    private final double coverageBonus;
    private final VectorSearchService vectorSearchService;
    
    public AssessmentServiceImpl(ScanClient scanClient,
                                 CveStoreClient cveClient,
                                 @Value("${ai.evidence.k-default:6}") int kDefault,
                                 @Value("${ai.risk.coverage-cap:10}") int coverageCap,
                                 @Value("${ai.risk.weights.epss:0.65}") double wEpss,
                                 @Value("${ai.risk.weights.cvss:0.35}") double wCvss,
                                 @Value("${ai.risk.weights.coverage-bonus:0.15}") double coverageBonus,
                                 VectorSearchService vectorSearchService) {
        this.scanClient = scanClient;
        this.cveClient = cveClient;
        this.kDefault = kDefault;
        this.coverageCap = coverageCap;
        this.wEpss = wEpss;
        this.wCvss = wCvss;
        this.coverageBonus = coverageBonus;
        this.vectorSearchService = vectorSearchService;
    }
    
    @Override
    public AssessImageResponse assessImage(AssessImageRequest request) {
        int k = request.k() != null ? request.k() : kDefault;
        
        // 1) Scan
        ScanResult scan = scanClient.scanImage(request.imageRef());
        if (scan == null || scan.findings() == null || scan.findings().isEmpty()) {
            return new AssessImageResponse(request.imageRef(), 0, RiskBand.LOW, List.of(),
                                           "No CVEs were found in this image by the scanner.", List.of());
        }
        
        // Flatten counts per CVE and collect package lists
        Map<String, List<String>> packagesByCve = new LinkedHashMap<>();
        for (ScanFinding f : scan.findings()) {
            if (f == null || f.cveId() == null) {
                continue;
            }
            
            var list = packagesByCve.computeIfAbsent(f.cveId(), id -> new ArrayList<>());
            var pkg = f.packageName();
            if (pkg != null && !pkg.isBlank()) {
                list.add(pkg);
            }
        }
        
        Map<String, Boolean> fixByCve = new LinkedHashMap<>();
        for (ScanFinding f : scan.findings()) {
            if (f == null || f.cveId() == null) {
                continue;
            }
            boolean hasFix = f.fixedVersion() != null && !f.fixedVersion().isBlank();
            if (hasFix) {
                fixByCve.put(f.cveId(), true);
            }
            else {
                fixByCve.putIfAbsent(f.cveId(), false);
            }
        }
        
        List<String> cveIds = new ArrayList<>(packagesByCve.keySet());
        
        // 2) Fetch CVE details (batch if possible)
        Map<String, Double> cvssByCve = new HashMap<>();
        for (ScanFinding f : scan.findings()) {
            if (f == null || f.cveId() == null || f.cvss() == null || f.cvss().score() == null) {
                continue;
            }
            double score = f.cvss().score();
            cvssByCve.merge(f.cveId(), score, Math::max);
        }
        Map<String, CveForEmbedding> details = cveClient.getByIds(cveIds);
        
        // 3) Build TopFinding list with risk scores
        List<TopFinding> candidates = new ArrayList<>();
        Map<String, Double> sCveById = new HashMap<>();
        
        for (String cveId : cveIds) {
            CveForEmbedding d = details.get(cveId);
            if (d == null) {
                continue;
            }
            
            double epss = d.epss() != null ? d.epss() : 0.0;
            double percentile = d.epssPercentile() != null ? d.epssPercentile() : 0.0;
            double cvss = (d.cvssBase() != null) ? d.cvssBase() : cvssByCve.getOrDefault(cveId, 0.0);
            int instances = packagesByCve.getOrDefault(cveId, List.of()).size();
            double coverageNorm = RiskScoring.coverageNormFromInstances(instances, coverageCap);
            
            double sCve = RiskScoring.perCveScore(epss, cvss, coverageNorm, wEpss, wCvss, coverageBonus);
            sCveById.put(cveId, sCve);
            
            String url = pickBestUrl(d);
            String summary = (d.title() != null && !d.title().isBlank()) ? d.title() : "Vulnerability " + cveId;
            
            TopFinding tf = new TopFinding(cveId, epss, percentile, cvss, packagesByCve.getOrDefault(cveId, List.of()),
                                           summary, url, fixByCve.getOrDefault(cveId, false));
            candidates.add(tf);
        }
        
        candidates.sort(Comparator.comparing((TopFinding t) -> sCveById.getOrDefault(t.cveId(), 0.0))
                                .reversed()
                                .thenComparing(TopFinding::epss, Comparator.nullsLast(Comparator.reverseOrder())));
        
        List<TopFinding> topFindings = candidates.stream().limit(k).toList();
        
        // 4) Overall image score (weighted by epss^2) + band
        Map<String, Double> epssMap = topFindings.stream()
                .collect(Collectors.toMap(TopFinding::cveId, TopFinding::epss, (a, b) -> a));
        int overall = RiskScoring.overallImageScore(topFindings, epssMap);
        RiskBand band = band(overall);
        
        final int perFinding = 2;
        final int maxTotal = 6;
        final double minSim = 0.62;
        
        List<Citation> citations = new ArrayList<>();
        for (TopFinding tf : topFindings) {
            List<Citation> sem = semanticCitationsFor(tf.cveId(), tf.packages(), tf.summary(), perFinding, minSim);
            if (sem.isEmpty()) {
                sem = List.of(new Citation(tf.cveId(), tf.url(), tf.summary()));
            }
            
            for (Citation c : sem) {
                if (citations.size() >= maxTotal) {
                    break;
                }
                citations.add(c);
            }
            if (citations.size() >= maxTotal) {
                break;
            }
        }
        
        String baseExplanation = switch (band) {
            case CRITICAL ->
                    "High likelihood of exploitation and severe impact across multiple packages. Prioritize " +
                            "immediate" + " patching and rebuild.";
            case HIGH ->
                    "Elevated risk: mix of high EPSS and high CVSS findings present. Patch the top issues and " +
                            "redeploy.";
            case MEDIUM -> "Moderate risk: review the listed CVEs and plan updates during the next maintenance window.";
            default -> "Low risk based on current EPSS and CVSS signals.";
        };
        String pkgHint = mostCommonPackagesSummary(topFindings, 3);
        String explanation = pkgHint.isBlank() ? baseExplanation :
                baseExplanation + " Most affected packages: " + pkgHint + ".";
        return new AssessImageResponse(request.imageRef(), overall, band, topFindings, explanation, citations);
    }
    
    private static RiskBand band(int score) {
        if (score >= 75) {
            return RiskBand.CRITICAL;
        }
        if (score >= 50) {
            return RiskBand.HIGH;
        }
        if (score >= 25) {
            return RiskBand.MEDIUM;
        }
        return RiskBand.LOW;
    }
    
    private static String pickBestUrl(CveForEmbedding d) {
        if (d.references() != null && !d.references().isEmpty()) {
            var first = d.references().get(0);
            if (first != null && first.url() != null && !first.url().isBlank()) {
                return first.url();
            }
        }
        
        return "https://nvd.nist.gov/vuln/detail/" + d.cveId();
    }
    
    private static String buildQuery(String cveId,
                                     List<String> pkgs,
                                     String fallbackTitle) {
        String pkgPart = (pkgs == null || pkgs.isEmpty()) ? "" : " " + String.join(", ", pkgs);
        String titlePart = (fallbackTitle != null && !fallbackTitle.isBlank()) ? (" " + fallbackTitle) : "";
        return cveId + pkgPart + titlePart;
    }
    
    private static String truncate(String s,
                                   int max) {
        if (s == null) {
            return null;
        }
        
        return s.length() <= max ? s : s.substring(0, Math.max(0, max - 1)) + "...";
    }
    
    private List<Citation> semanticCitationsFor(String cveId,
                                                List<String> pkgs,
                                                String titleOrDesc,
                                                int k,
                                                double minSim) {
        String q = buildQuery(cveId, pkgs, titleOrDesc);
        List<SearchHit> hits = vectorSearchService.search(q, Math.max(k, 1));
        return hits.stream()
                .filter(h -> h.similarity() == null || h.similarity() >= minSim)
                .limit(k)
                .map(h -> new Citation(h.cveId(), "https://nvd.nist.gov/vuln/detail/" + h.cveId(),
                                       h.title() != null && !h.title().isBlank() ? h.title() : truncate(h.description(),
                                                                                                        180) != null
                                               ? truncate(
                                               h.description(), 180) : ("Relevant evidence for " + h.cveId())))
                .toList();
    }
    
    private static String mostCommonPackagesSummary(List<TopFinding> tfs,
                                                    int maxPkgs) {
        Map<String, Integer> freq = new LinkedHashMap<>();
        for (TopFinding tf : tfs) {
            if (tf.packages() == null) {
                continue;
            }
            for (String p : tf.packages()) {
                freq.merge(p, 1, Integer::sum);
            }
        }
        return freq.entrySet()
                .stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(maxPkgs)
                .map(Map.Entry::getKey)
                .collect(Collectors.joining(", "));
    }
}
