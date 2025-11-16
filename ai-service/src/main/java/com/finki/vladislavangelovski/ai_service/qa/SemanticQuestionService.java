package com.finki.vladislavangelovski.ai_service.qa;

import com.finki.vladislavangelovski.ai_service.search.VectorSearchService;
import com.finki.vladislavangelovski.ai_service.search.dto.SearchHit;
import com.finki.vladislavangelovski.common.dto.Citation;
import com.finki.vladislavangelovski.common.dto.QaQuestionRequest;
import com.finki.vladislavangelovski.common.dto.QaQuestionResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class SemanticQuestionService {
    private final VectorSearchService vectorSearchService;
    
    public SemanticQuestionService(VectorSearchService vectorSearchService) {
        this.vectorSearchService = vectorSearchService;
    }
    
    public QaQuestionResponse answer(QaQuestionRequest request) {
        String question = request.question();
        int k = (request.k() != null && request.k() > 0 && request.k() <= 20) ? request.k() : 5;
        
        long t0 = System.currentTimeMillis();
        List<SearchHit> hits = vectorSearchService.search(question, k);
        long took = System.currentTimeMillis() - t0;
        
        if (hits.isEmpty()) {
            String answer = "I couldn't any relevant CVEs for this question in the indexed data.";
            return new QaQuestionResponse(answer, List.of(), List.of(), List.of());
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("I found ")
                .append(hits.size())
                .append(" relevant CVEs for this question (")
                .append(question)
                .append(").\n")
                .append("Search time: ")
                .append(took)
                .append(" ms\n");
        
        for (int i = 0; i < hits.size(); i++) {
            SearchHit h = hits.get(i);
            sb.append(i + 1).append(". ").append(h.cveId());
            
            if (h.description() != null && !h.description().isBlank()) {
                sb.append(" - ").append(h.description());
            }
            
            if (h.epss() != null) {
                sb.append(" (EPSS ").append(String.format("%.3f", h.epss())).append(")");
            }
            
            if (h.cvssBase() != null) {
                sb.append(" [CVSS ").append(String.format("%.1f", h.cvssBase())).append("]");
            }
            sb.append("\n\n");
        }
        
        String answer = sb.toString();
        
        List<Citation> citations = hits.stream()
                .map(h -> new Citation(h.cveId(), "https://nvd.nist.gov/vuln/detail/" + h.cveId(),
                                       h.description() != null && !h.description()
                                               .isBlank() ? h.description() : ("Vulnerability " + h.cveId())))
                .toList();
        
        List<String> usedCves = hits.stream().map(SearchHit::cveId).toList();
        
        List<String> usedPackages = List.of();
        
        return new QaQuestionResponse(answer, citations, usedCves, usedPackages);
    }
}
