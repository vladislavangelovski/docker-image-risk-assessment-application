package com.finki.vladislavangelovski.ai_service.qa;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finki.vladislavangelovski.ai_service.clients.cve.CveStoreClient;
import com.finki.vladislavangelovski.ai_service.clients.scan.dto.ScanFinding;
import com.finki.vladislavangelovski.ai_service.search.VectorSearchService;
import com.finki.vladislavangelovski.ai_service.search.dto.SearchHit;
import com.finki.vladislavangelovski.common.dto.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class SemanticClaimService {
    private static final int RETRIEVAL_K = 20;
    private static final int DEFAULT_EVIDENCE_TOP_N = 6;
    private static final int MAX_EVIDENCE_TOP_N = 10;
    private static final int DESCRIPTION_MAX_LEN = 600;
    
    private static final Pattern CVE_ID_PATTERN = Pattern.compile("CVE-\\d{4}-\\d{4,}");
    
    private final VectorSearchService vectorSearchService;
    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;
    private final CveStoreClient cveStoreClient;
    private final PromptTemplates promptTemplates;
    
    public SemanticClaimService(VectorSearchService vectorSearchService,
                                ChatClient chatClient,
                                ObjectMapper objectMapper,
                                CveStoreClient cveStoreClient,
                                PromptTemplates promptTemplates) {
        this.vectorSearchService = vectorSearchService;
        this.chatClient = chatClient;
        this.objectMapper = objectMapper;
        this.cveStoreClient = cveStoreClient;
        this.promptTemplates = promptTemplates;
    }
    
    public QaClaimResponse judgeClaim(QaClaimRequest request,
                                      Set<String> allowedCves,
                                      Map<String, List<String>> packagesByCve,
                                      List<ScanFinding> scanFindings) {
        
        String claim = request.claim();
        if (claim == null || claim.isBlank()) {
            throw new IllegalArgumentException("Claim must not be null or blank");
        }
        
        int evidenceN = resolveEvidenceTopN(request.topK());
        
        // 1) Vector search (semantic)
        List<SearchHit> hits = vectorSearchService.search(claim, RETRIEVAL_K);
        
        // 2) If we have an image scan restriction, keep only CVEs that exist in the image
        if (allowedCves != null && !allowedCves.isEmpty()) {
            hits = hits.stream()
                    .filter(h -> h.cveId() != null && allowedCves.contains(h.cveId()))
                    .toList();
        }
        
        List<SearchHit> evidence = topN(hits, evidenceN);
        List<Citation> citations = buildCitations(evidence);
        String evidenceText = "";
        
        // 3) Fallback: if semantic evidence is empty, build evidence from scan findings
        if (evidence.isEmpty()) {
            boolean usedFallback = false;
            if (scanFindings != null && !scanFindings.isEmpty()) {
                ScanFallback fallback = buildEvidenceFromScan(claim, scanFindings, evidenceN);
                
                if (!fallback.citations().isEmpty()
                        && fallback.evidenceText() != null
                        && !fallback.evidenceText().isBlank()) {
                    citations = fallback.citations();
                    evidenceText = fallback.evidenceText();
                    usedFallback = true;
                }
            }

            if (!usedFallback) {
                List<SearchHit> storeHits = buildEvidenceFromCveStore(claim, allowedCves, evidenceN);
                if (!storeHits.isEmpty()) {
                    citations = buildCitations(storeHits);
                    evidenceText = buildEvidenceText(storeHits, packagesByCve);
                    usedFallback = true;
                }
            }

            if (!usedFallback) {
                return new QaClaimResponse(
                        Verdict.INSUFFICIENT,
                        "Insufficient evidence: no relevant CVE context was retrieved for this claim.",
                        List.of()
                );
            }
        } else {
            evidenceText = buildEvidenceText(evidence, packagesByCve);
        }
        
        // 4) Ask the model for strict JSON verdict
        ModelOutput out = callModel(claim, evidenceText, allowedCves, packagesByCve);
        if (out == null) {
            return new QaClaimResponse(
                    Verdict.INSUFFICIENT,
                    "Insufficient evidence: the model output could not be parsed as strict JSON.",
                    citations
            );
        }
        
        Verdict verdict = parseVerdict(out.verdict());
        List<String> rationaleBullets = sanitizeRationale(out.rationale());
        List<String> modelCveIds = sanitizeCveIds(out.cveIds());
        
        // 5) Evidence CVEs (ground truth) come from citations we generated (semantic or scan fallback)
        Set<String> evidenceCves = new LinkedHashSet<>();
        for (Citation c : citations) {
            if (c.cveId() != null && !c.cveId().isBlank()) {
                evidenceCves.add(c.cveId());
            }
        }
        if (allowedCves != null && !allowedCves.isEmpty()) {
            evidenceCves.retainAll(allowedCves);
        }
        
        // --- Guardrails ---
        Set<String> mentionedCves = extractMentionedCves(claim);
        Set<String> mentionedGrounded = new LinkedHashSet<>(mentionedCves);
        mentionedGrounded.retainAll(evidenceCves);
        
        boolean forcedByMentionedCve = false;
        
        // Guardrail A: presence claim with grounded mentioned CVE => SUPPORTS + clean rationale
        if (!mentionedGrounded.isEmpty() && looksLikePositivePresenceClaim(claim)) {
            verdict = Verdict.SUPPORTS;
            forcedByMentionedCve = true;
            
            String cves = String.join(", ", mentionedGrounded);
            rationaleBullets = List.of(
                    "The image scan detected " + cves + ".",
                    "This directly supports the claim."
            );
        }
        
        // Guardrail B: exploitability-likelihood requires EPSS/exploit signals; otherwise INSUFFICIENT
        if (looksLikeExploitabilityLikelihoodClaim(claim) && !hasExploitabilitySignals(evidenceText)) {
            verdict = Verdict.INSUFFICIENT;
            
            rationaleBullets = List.of(
                    "The scan shows vulnerabilities, but exploitability likelihood cannot be confirmed without EPSS/known-exploit evidence.",
                    "CVSS/severity alone is not enough to claim high-likelihood exploitation."
            );
        }
        
        // 6) Grounding set: model CVEs limited to evidence CVEs, or forced to mentionedGrounded
        Set<String> groundedCves = new LinkedHashSet<>(modelCveIds);
        groundedCves.retainAll(evidenceCves);
        
        if (forcedByMentionedCve) {
            groundedCves = mentionedGrounded; // already grounded by definition
        }
        
        // If verdict is strong but nothing is grounded, downgrade
        if ((verdict == Verdict.SUPPORTS || verdict == Verdict.REFUTES) && groundedCves.isEmpty()) {
            verdict = Verdict.INSUFFICIENT;
        }
        
        String rationale = formatRationale(verdict, rationaleBullets, groundedCves);
        return new QaClaimResponse(verdict, rationale, citations);
    }
    
    
    public QaClaimResponse judgeClaim(QaClaimRequest request,
                                      Set<String> allowedCves,
                                      Map<String, List<String>> packagesByCve) {
        return judgeClaim(request, allowedCves, packagesByCve, null);
    }
    
    
    private ModelOutput callModel(String claim,
                                  String evidenceText,
                                  Set<String> allowedCves,
                                  Map<String, List<String>> packagesByCve) {
        String systemPrompt = promptTemplates.claimSystem();
        
        String detectedCvesStr = (allowedCves == null || allowedCves.isEmpty())
                ? "None. No scan-based CVE list provided; using semantic search only."
                : String.join(", ", allowedCves);
        
        String packagesByCveStr;
        if (packagesByCve == null || packagesByCve.isEmpty()) {
            packagesByCveStr = "No package info from image scan.";
        } else {
            StringBuilder pkgSb = new StringBuilder();
            packagesByCve.forEach((cve, pkgs) -> pkgSb.append(cve)
                    .append(": ")
                    .append(String.join(", ", pkgs))
                    .append("\n"));
            packagesByCveStr = pkgSb.toString().trim();
        }
        
        String userPrompt = String.format(Locale.ROOT, promptTemplates.claimUser(),
                                          claim, evidenceText, detectedCvesStr, packagesByCveStr);
        
        String raw = chatClient.prompt().system(systemPrompt).user(userPrompt).call().content();
        if (raw == null || raw.isBlank()) {
            return null;
        }
        
        return tryParseModelOutput(raw);
    }
    
    private ModelOutput tryParseModelOutput(String raw) {
        String trimmed = raw.trim();
        try {
            return objectMapper.readValue(trimmed, ModelOutput.class);
        } catch (JsonProcessingException e) {
            // Best-effort: extract the first {...} block in case the model returns extra text.
            int start = trimmed.indexOf('{');
            int end = trimmed.lastIndexOf('}');
            if (start >= 0 && end > start) {
                String candidate = trimmed.substring(start, end + 1);
                try {
                    return objectMapper.readValue(candidate, ModelOutput.class);
                } catch (JsonProcessingException ignored) {
                    log.debug("Failed to parse model JSON output. Raw: {}", trimmed);
                    return null;
                }
            }
            log.debug("Failed to parse model JSON output. Raw: {}", trimmed);
            return null;
        }
    }
    
    private static int resolveEvidenceTopN(Integer topK) {
        if (topK == null) return DEFAULT_EVIDENCE_TOP_N;
        int v = topK;
        if (v < 1) return DEFAULT_EVIDENCE_TOP_N;
        if (v > MAX_EVIDENCE_TOP_N) return MAX_EVIDENCE_TOP_N;
        return v;
    }
    
    private static List<SearchHit> topN(List<SearchHit> hits, int n) {
        if (hits == null || hits.isEmpty()) return List.of();
        int limit = Math.min(n, hits.size());
        return hits.subList(0, limit);
    }
    
    private static String buildEvidenceText(List<SearchHit> hits,
                                            Map<String, List<String>> packagesByCve) {
        if (hits == null || hits.isEmpty()) return "No CVE evidence available.";
        
        StringBuilder sb = new StringBuilder();
        int idx = 1;
        for (SearchHit hit : hits) {
            String cveId = Objects.toString(hit.cveId(), "UNKNOWN");
            String title = Objects.toString(hit.title(), "N/A");
            String desc = truncate(Objects.toString(hit.description(), ""), DESCRIPTION_MAX_LEN);
            Double cvss = hit.cvssBase();
            Double epss = hit.epss();
            
            sb.append(idx).append(") ").append(cveId);
            if (cvss != null) sb.append(" (CVSS ").append(String.format("%.1f", cvss)).append(")");
            if (epss != null) sb.append(" [EPSS ").append(String.format("%.3f", epss)).append("]");
            sb.append("\n   Title: ").append(title);
            if (!desc.isBlank()) sb.append("\n   Summary: ").append(desc);
            
            if (packagesByCve != null && cveId != null) {
                List<String> pkgs = packagesByCve.get(cveId);
                if (pkgs != null && !pkgs.isEmpty()) {
                    sb.append("\n   Packages in image: ").append(String.join(", ", pkgs));
                }
            }
            
            sb.append("\n\n");
            idx++;
        }
        return sb.toString().trim();
    }
    
    private static List<Citation> buildCitations(List<SearchHit> hits) {
        List<Citation> result = new ArrayList<>();
        if (hits == null) return result;
        
        for (SearchHit hit : hits) {
            if (hit.cveId() == null || hit.cveId().isBlank()) continue;
            
            String cveId = hit.cveId();
            String url = "https://nvd.nist.gov/vuln/detail/" + cveId;
            String title = (hit.title() == null || hit.title().isBlank()) ? cveId : hit.title();
            
            result.add(new Citation(cveId, url, title));
        }
        return result;
    }

    private List<SearchHit> buildEvidenceFromCveStore(String text,
                                                      Set<String> allowedCves,
                                                      int topN) {
        Set<String> candidates = new LinkedHashSet<>();
        if (allowedCves != null && !allowedCves.isEmpty()) {
            candidates.addAll(allowedCves);
        } else {
            candidates.addAll(extractMentionedCves(text));
        }

        if (candidates.isEmpty()) {
            return List.of();
        }

        List<String> ids = candidates.stream().limit(topN).toList();
        Map<String, CveForEmbedding> byId;
        try {
            byId = cveStoreClient.getByIds(ids);
        } catch (Exception ex) {
            log.debug("CVE store fallback failed for IDs {}", ids, ex);
            return List.of();
        }

        if (byId == null || byId.isEmpty()) {
            return List.of();
        }

        List<SearchHit> hits = new ArrayList<>();
        for (String id : ids) {
            CveForEmbedding cve = byId.get(id);
            if (cve != null) {
                hits.add(toSearchHit(cve));
            }
        }
        return hits;
    }

    private static SearchHit toSearchHit(CveForEmbedding cve) {
        return new SearchHit(
                cve.cveId(),
                cve.title(),
                cve.description(),
                cve.epss(),
                cve.cvssBase(),
                null
        );
    }
    
    private static String truncate(String text, int maxLen) {
        if (text == null) return "";
        if (text.length() <= maxLen) return text;
        return text.substring(0, maxLen - 3) + "...";
    }
    
    private static Verdict parseVerdict(String verdict) {
        if (verdict == null) return Verdict.INSUFFICIENT;
        try {
            return Verdict.valueOf(verdict.trim().toUpperCase(Locale.ROOT));
        } catch (Exception ignored) {
            return Verdict.INSUFFICIENT;
        }
    }
    
    private static List<String> sanitizeRationale(List<String> rationale) {
        if (rationale == null) return List.of();
        return rationale.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .limit(4)
                .toList();
    }
    
    private static List<String> sanitizeCveIds(List<String> cveIds) {
        if (cveIds == null) return List.of();
        
        List<String> out = new ArrayList<>();
        for (String s : cveIds) {
            if (s == null) continue;
            String t = s.trim().toUpperCase(Locale.ROOT);
            Matcher m = CVE_ID_PATTERN.matcher(t);
            if (m.find()) out.add(m.group());
        }
        return out.stream().distinct().toList();
    }
    
    private static String formatRationale(Verdict verdict,
                                          List<String> bullets,
                                          Set<String> groundedCves) {
        List<String> effective = new ArrayList<>(bullets != null ? bullets : List.of());
        if (effective.isEmpty()) {
            switch (verdict) {
                case SUPPORTS -> effective.add("Evidence suggests the claim is supported by the retrieved CVE context.");
                case REFUTES -> effective.add("Evidence suggests the claim is contradicted by the retrieved CVE context.");
                default -> effective.add("Insufficient evidence in the retrieved CVE context to confirm or deny the claim.");
            }
        }
        if (groundedCves != null && !groundedCves.isEmpty()) {
            effective.add("Grounded CVEs: " + String.join(", ", groundedCves));
        }
        
        StringBuilder sb = new StringBuilder();
        for (String b : effective) sb.append("- ").append(b).append("\n");
        return sb.toString().trim();
    }
    
    /** Strict structured output we expect from the LLM. */
    public record ModelOutput(String verdict, List<String> rationale, List<String> cveIds) {}
    
    private ScanFallback buildEvidenceFromScan(String claim, List<ScanFinding> findings, int topN) {
        // Map CVE -> best finding (first seen)
        var byCve = new java.util.LinkedHashMap<String, ScanFinding>();
        for (ScanFinding f : findings) {
            if (f == null || f.cveId() == null || f.cveId().isBlank()) continue;
            byCve.putIfAbsent(f.cveId(), f);
        }
        
        // CVEs mentioned in the claim
        var mentioned = new java.util.LinkedHashSet<String>();
        if (claim != null) {
            Matcher m = CVE_ID_PATTERN.matcher(claim.toUpperCase());
            while (m.find()) mentioned.add(m.group());
        }
        
        var selected = new java.util.ArrayList<ScanFinding>();
        
        // Prefer mentioned CVEs that exist in the scan
        for (String cve : mentioned) {
            ScanFinding f = byCve.get(cve);
            if (f != null) selected.add(f);
            if (selected.size() >= topN) break;
        }
        
        // If none mentioned (or not enough), take top findings from scan by CVSS then severity
        if (selected.size() < topN) {
            byCve.values().stream()
                    .sorted((a, b) -> {
                        double sa = safeScore(a);
                        double sb = safeScore(b);
                        int cmpScore = Double.compare(sb, sa);
                        if (cmpScore != 0) return cmpScore;
                        return Integer.compare(severityRank(b), severityRank(a));
                    })
                    .filter(f -> !selected.contains(f))
                    .limit(topN - selected.size())
                    .forEach(selected::add);
        }
        
        // Build citations + evidence
        var citations = new java.util.ArrayList<Citation>();
        var sb = new StringBuilder();
        
        int idx = 1;
        for (ScanFinding f : selected) {
            String cve = f.cveId();
            
            // ---- EPSS enrichment (best-effort, never fail the request) ----
            EpssScoreDto epss = null;
            try {
                epss = cveStoreClient.getLatestEpss(cve).orElse(null);
            } catch (Exception ignored) {
                // ignore
            }
            
            String epssSnippet = "";
            if (epss != null && epss.getScore() != null) {
                String scoreStr = epss.getScore().stripTrailingZeros().toPlainString();
                String percStr = (epss.getPercentile() != null)
                        ? epss.getPercentile().stripTrailingZeros().toPlainString()
                        : "null";
                epssSnippet = " | EPSS " + scoreStr + " (p=" + percStr + ")";
            }
            
            String url = pickBestUrl(f.references(), cve);
            String title = "Scan finding: " + cve + " (" + safeStr(f.packageName()) + " " + safeStr(f.installedVersion()) + ")" + epssSnippet;
            
            citations.add(new Citation(cve, url, title));
            
            sb.append(idx++).append(") ").append(cve);
            
            if (f.severity() != null && !f.severity().isBlank()) {
                sb.append(" [").append(f.severity().toUpperCase()).append("]");
            }
            if (f.cvss() != null && f.cvss().score() != null) {
                sb.append(" (CVSS ").append(String.format(java.util.Locale.ROOT, "%.1f", f.cvss().score())).append(")");
            }
            
            if (!epssSnippet.isBlank()) {
                sb.append("\n   ").append(epssSnippet.substring(3)); // remove leading " | "
            }
            
            if (f.cvss() != null && f.cvss().vector() != null && !f.cvss().vector().isBlank()) {
                sb.append("\n   Vector: ").append(f.cvss().vector());
            }
            
            sb.append("\n   Package: ").append(safeStr(f.packageName()));
            if (f.installedVersion() != null && !f.installedVersion().isBlank()) {
                sb.append(" (installed ").append(f.installedVersion()).append(")");
            }
            if (f.fixedVersion() != null && !f.fixedVersion().isBlank()) {
                sb.append(", fixed in ").append(f.fixedVersion());
            }
            
            if (f.sourceTarget() != null && !f.sourceTarget().isBlank()) {
                sb.append("\n   Target: ").append(f.sourceTarget());
            }
            
            if (f.references() != null && !f.references().isEmpty()) {
                sb.append("\n   Reference: ").append(f.references().get(0));
            }
            
            sb.append("\n\n");
        }
        
        return new ScanFallback(sb.toString().trim(), citations);
    }
    
    
    private static double safeScore(ScanFinding f) {
        if (f == null || f.cvss() == null || f.cvss().score() == null) return 0.0;
        return f.cvss().score();
    }
    
    private static int severityRank(ScanFinding f) {
        if (f == null || f.severity() == null) return 0;
        return switch (f.severity().trim().toUpperCase(java.util.Locale.ROOT)) {
            case "CRITICAL" -> 4;
            case "HIGH" -> 3;
            case "MEDIUM" -> 2;
            case "LOW" -> 1;
            default -> 0;
        };
    }
    
    private static String pickBestUrl(java.util.List<String> refs, String cve) {
        if (refs != null) {
            for (String r : refs) {
                if (r == null) continue;
                if (r.contains("nvd.nist.gov/vuln/detail/")) return r;
            }
            for (String r : refs) {
                if (r == null) continue;
                if (r.startsWith("http")) return r;
            }
        }
        return "https://nvd.nist.gov/vuln/detail/" + cve;
    }
    
    private static String safeStr(String s) {
        return (s == null || s.isBlank()) ? "N/A" : s.trim();
    }
    
    private record ScanFallback(String evidenceText, java.util.List<Citation> citations) {}
    
    private static Set<String> extractMentionedCves(String text) {
        Set<String> out = new LinkedHashSet<>();
        if (text == null) return out;
        Matcher m = CVE_ID_PATTERN.matcher(text.toUpperCase(Locale.ROOT));
        while (m.find()) out.add(m.group());
        return out;
    }
    
    private static boolean looksLikePositivePresenceClaim(String claim) {
        if (claim == null) return false;
        String c = claim.toLowerCase(Locale.ROOT);
        return c.contains("affected by")
                || c.contains("is affected")
                || c.contains("vulnerable to")
                || c.contains("contains cve")
                || c.contains("has cve")
                || c.contains("is affected by cve")
                || c.contains("is vulnerable");
    }
    
    private static boolean looksLikeExploitabilityLikelihoodClaim(String claim) {
        if (claim == null) return false;
        String c = claim.toLowerCase(Locale.ROOT);
        return c.contains("high-likelihood exploitable")
                || c.contains("high likelihood exploitable")
                || c.contains("highly exploitable")
                || (c.contains("likely") && c.contains("exploitable"))
                || c.contains("likely to be exploited");
    }
    
    private static boolean hasExploitabilitySignals(String evidenceText) {
        if (evidenceText == null) return false;
        String e = evidenceText.toLowerCase(Locale.ROOT);
        
        // If EPSS is present OR evidence explicitly references exploit/kev, treat as signal.
        return e.contains("epss")
                || e.contains("known exploited")
                || e.contains("kev")
                || e.contains("exploitdb")
                || e.contains("metasploit")
                || e.contains("proof of concept")
                || e.contains("poc");
    }
    
}
