package com.finki.vladislavangelovski.ai_service.qa;

import com.finki.vladislavangelovski.ai_service.search.VectorSearchService;
import com.finki.vladislavangelovski.ai_service.search.dto.SearchHit;
import com.finki.vladislavangelovski.common.dto.Citation;
import com.finki.vladislavangelovski.common.dto.QaQuestionRequest;
import com.finki.vladislavangelovski.common.dto.QaQuestionResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
public class SemanticQuestionService {
    
    private static final int RETRIEVAL_K = 20;
    private static final int EVIDENCE_TOP_N = 6;
    private static final int DESCRIPTION_MAX_LEN = 600;
    
    private final VectorSearchService vectorSearchService;
    private final ChatClient chatClient;
    
    public SemanticQuestionService(VectorSearchService vectorSearchService,
                                   ChatClient chatClient) {
        this.vectorSearchService = vectorSearchService;
        this.chatClient = chatClient;
    }
    
    private static List<SearchHit> topN(List<SearchHit> hits,
                                        int n) {
        if (hits == null || hits.isEmpty()) {
            return List.of();
        }
        int limit = Math.min(n, hits.size());
        return hits.subList(0, limit);
    }
    
    private static String buildEvidenceText(List<SearchHit> hits,
                                            Map<String, List<String>> packagesByCve) {
        if (hits == null || hits.isEmpty()) {
            return "No CVE evidence available.";
        }
        
        StringBuilder sb = new StringBuilder();
        int idx = 1;
        for (SearchHit hit : hits) {
            String cveId = Objects.toString(hit.cveId(), "UNKNOWN");
            String title = Objects.toString(hit.title(), "N/A");
            String desc = truncate(Objects.toString(hit.description(), ""), DESCRIPTION_MAX_LEN);
            Double cvss = hit.cvssBase();
            Double epss = hit.epss();
            
            sb.append(idx).append(") ").append(cveId);
            if (cvss != null) {
                sb.append(" (CVSS ").append(String.format("%.1f", cvss)).append(")");
            }
            if (epss != null) {
                sb.append(" [EPSS ").append(String.format("%.3f", epss)).append(")");
            }
            sb.append("\n   Title: ").append(title);
            if (!desc.isBlank()) {
                sb.append("\n   Summary: ").append(desc);
            }
            
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
        if (hits == null) {
            return result;
        }
        for (SearchHit hit : hits) {
            if (hit.cveId() == null || hit.cveId().isBlank()) {
                continue;
            }
            String cveId = hit.cveId();
            String url = "https://nvd.nist.gov/vuln/detail/" + cveId;
            String title = hit.title();
            if (title == null || title.isBlank()) {
                title = cveId;
            }
            result.add(new Citation(cveId, url, title));
        }
        return result;
    }
    
    private static String truncate(String text,
                                   int maxLen) {
        if (text == null) {
            return "";
        }
        if (text.length() <= maxLen) {
            return text;
        }
        return text.substring(0, maxLen - 3) + "...";
    }
    
    public QaQuestionResponse answerQuestion(QaQuestionRequest request) {
        return answer(request, null, null);
    }
    
    public QaQuestionResponse answerQuestionForImage(QaQuestionRequest request,
                                                     Set<String> allowedCves,
                                                     Map<String, List<String>> packagesByCve) {
        return answer(request, allowedCves, packagesByCve);
    }
    
    private QaQuestionResponse answer(QaQuestionRequest request,
                                      Set<String> allowedCves,
                                      Map<String, List<String>> packagesByCve) {
        String question = request.question();
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("Question must not be null or blank");
        }
        
        List<SearchHit> hits = vectorSearchService.search(question, RETRIEVAL_K);
        
        if (allowedCves != null && !allowedCves.isEmpty()) {
            hits = hits.stream().filter(h -> h.cveId() != null && allowedCves.contains(h.cveId())).toList();
        }
        
        List<SearchHit> evidence = topN(hits, EVIDENCE_TOP_N);
        
        String evidenceText = buildEvidenceText(evidence, packagesByCve);
        List<Citation> citations = buildCitations(evidence);
        
        String systemPrompt = """
                You are a container security assistant for DevOps teams.
                
                You MUST obey these rules:
                
                1. You ONLY know about CVEs, packages and scores that are explicitly provided
                   in the <cve_context> and <image_context> sections.
                   - If the user mentions a CVE that is NOT present there, you MUST say
                     that you have no evidence for that CVE in the current context.
                2. Do NOT invent:
                   - CVE details (affected product, package, version, exploit vector, etc.)
                   - Package names or versions
                   - Vendors or products (e.g. Docker, Ivanti, Kubernetes) unless they
                     explicitly appear in the provided context.
                3. When you talk about risk, base it ONLY on:
                   - The descriptions, CVSS scores, EPSS scores, and other fields present
                     in the context.
                4. If the evidence is weak or unrelated to the question, say that you do NOT
                   have enough information and clearly mark the answer as uncertain.
                
                Your job is to:
                - Explain the risk of the vulnerabilities for container images and DevOps teams.
                - Use short, concrete bullet points (no fluff).
                - Always reference the relevant CVE IDs in your explanation.
                """;
        
        String allowedCvesStr = (allowedCves == null || allowedCves.isEmpty())
                ? "None. No restrictions from image scan; using semantic search only."
                : String.join(", ", allowedCves);
        
        String packagesByCveStr;
        if (packagesByCve == null || packagesByCve.isEmpty()) {
            packagesByCveStr = "No package info from image scan.";
        } else {
            StringBuilder pkgSb = new StringBuilder();
            packagesByCve.forEach((cve, pkgs) -> {
                pkgSb.append(cve)
                        .append(": ")
                        .append(String.join(", ", pkgs))
                        .append("\n");
            });
            packagesByCveStr = pkgSb.toString().trim();
        }
        
        String userPrompt = """
                User question:
                %s

                <cve_context>
                %s
                </cve_context>

                <image_context>
                Allowed CVEs from the image (if any):
                %s

                Packages per CVE (if any):
                %s
                </image_context>

                Now:
                1. Answer the question using ONLY the information from <cve_context> and <image_context>.
                2. If the question asks about a CVE not present in this context, explicitly say
                   that you have no evidence about that CVE in the current context.
                3. Explain the risk in 2–4 bullet points, focused on container / DevOps impact.
                """.formatted(
                question,
                evidenceText,
                allowedCvesStr,
                packagesByCveStr
        );
        
        String answer = chatClient.prompt().system(systemPrompt).user(userPrompt).call().content();
        
        List<String> usedCves = citations.stream().map(Citation::cveId).filter(Objects::nonNull).distinct().toList();
        
        List<String> usedPackages = List.of();
        if (packagesByCve != null && !packagesByCve.isEmpty()) {
            usedPackages = usedCves.stream()
                    .map(cve -> packagesByCve.getOrDefault(cve, List.of()))
                    .flatMap(List::stream)
                    .distinct()
                    .toList();
        }
        
        return new QaQuestionResponse(answer, citations, usedCves, usedPackages);
    }
}
