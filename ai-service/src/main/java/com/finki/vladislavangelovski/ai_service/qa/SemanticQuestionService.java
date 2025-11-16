package com.finki.vladislavangelovski.ai_service.qa;

import com.finki.vladislavangelovski.ai_service.search.VectorSearchService;
import com.finki.vladislavangelovski.ai_service.search.dto.SearchHit;
import com.finki.vladislavangelovski.common.dto.Citation;
import com.finki.vladislavangelovski.common.dto.QaQuestionRequest;
import com.finki.vladislavangelovski.common.dto.QaQuestionResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
public class SemanticQuestionService {
    private final VectorSearchService vectorSearchService;
    
    public SemanticQuestionService(VectorSearchService vectorSearchService) {
        this.vectorSearchService = vectorSearchService;
    }
    
    public QaQuestionResponse answerQuestion(QaQuestionRequest request) {
        return answerQuestionInternal(request, null, null);
    }
    
    public QaQuestionResponse answerQuestionForImage(QaQuestionRequest request,
                                                     Set<String> allowedCves,
                                                     Map<String, List<String>> packagesByCve) {
        return answerQuestionInternal(request, allowedCves, packagesByCve);
    }
    
    private QaQuestionResponse answerQuestionInternal(QaQuestionRequest request,
                                                      Set<String> allowedCves,
                                                      // null = no restriction
                                                      Map<String, List<String>> packagesByCve
                                                      // null = no packages
    ) {
        int k = (request.k() != null && request.k() > 0 && request.k() <= 20) ? request.k() : 5;
        
        long start = System.currentTimeMillis();
        
        int rawK = (allowedCves != null && !allowedCves.isEmpty()) ? k * 2 : k;
        List<SearchHit> hits = vectorSearchService.search(request.question(), rawK);
        
        if (allowedCves != null && !allowedCves.isEmpty()) {
            hits = hits.stream().filter(h -> allowedCves.contains(h.cveId())).limit(k).toList();
            
            if (hits.isEmpty()) {
                hits = vectorSearchService.search(request.question(), k);
            }
        }
        else {
            if (hits.size() > k) {
                hits = hits.subList(0, k);
            }
        }
        
        long took = System.currentTimeMillis() - start;
        
        if (hits.isEmpty()) {
            return new QaQuestionResponse("I couldn't find any CVEs matching that question.", List.of(), List.of(),
                                          List.of());
        }
        
        StringBuilder answer = new StringBuilder();
        answer.append("I found ")
                .append(hits.size())
                .append(" relevant CVEs for this question (")
                .append(request.question())
                .append(").\n")
                .append("Search time: ")
                .append(took)
                .append(" ms\n");
        
        List<Citation> citations = new ArrayList<>();
        List<String> usedCves = new ArrayList<>();
        List<String> usedPackages = new ArrayList<>();
        
        for (int i = 0; i < hits.size(); i++) {
            SearchHit h = hits.get(i);
            usedCves.add(h.cveId());
            
            String desc = h.description() != null && !h.description()
                    .isBlank() ? h.description() : "(no description available)";
            
            List<String> pkgsForCve = (packagesByCve != null && h.cveId() != null) ? packagesByCve.getOrDefault(
                    h.cveId(), List.of()) : List.of();
            
            answer.append("\n").append(i + 1).append(". ").append(h.cveId()).append(" - ").append(desc);
            
            if (!pkgsForCve.isEmpty()) {
                answer.append(" Impacted packages: ").append(String.join(", ", pkgsForCve)).append(".");
            }
            
            if (h.epss() != null) {
                answer.append(" (EPSS ").append(String.format(Locale.ROOT, "%.3f", h.epss())).append(")");
            }
            
            String url = "https://nvd.nist.gov/vuln/detail/" + h.cveId();
            citations.add(new Citation(h.cveId(), url, desc));
            usedPackages.addAll(pkgsForCve);
        }
        
        // Deduplicate packages
        List<String> uniquePackages = usedPackages.stream().distinct().toList();
        
        return new QaQuestionResponse(answer.toString(), citations, usedCves, uniquePackages);
    }
}
