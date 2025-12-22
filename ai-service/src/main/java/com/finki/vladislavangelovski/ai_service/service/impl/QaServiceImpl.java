package com.finki.vladislavangelovski.ai_service.service.impl;

import com.finki.vladislavangelovski.ai_service.clients.scan.dto.ScanClient;
import com.finki.vladislavangelovski.ai_service.clients.scan.dto.ScanFinding;
import com.finki.vladislavangelovski.ai_service.clients.scan.dto.ScanResult;
import com.finki.vladislavangelovski.ai_service.qa.SemanticClaimService;
import com.finki.vladislavangelovski.ai_service.qa.SemanticQuestionService;
import com.finki.vladislavangelovski.ai_service.service.QaService;
import com.finki.vladislavangelovski.common.dto.*;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class QaServiceImpl implements QaService {
    
    private final SemanticQuestionService semanticQuestionService;
    private final SemanticClaimService semanticClaimService;
    private final ScanClient scanClient;
    
    public QaServiceImpl(SemanticQuestionService semanticQuestionService,
                         SemanticClaimService semanticClaimService,
                         ScanClient scanClient) {
        this.semanticQuestionService = semanticQuestionService;
        this.semanticClaimService = semanticClaimService;
        this.scanClient = scanClient;
    }
    
    @Override
    public QaQuestionResponse answerQuestion(QaQuestionRequest request) {
        String imageRef = request.imageRef();
        boolean hasImage = imageRef != null && !imageRef.isBlank();
        
        if (!hasImage) {
            return semanticQuestionService.answerQuestion(request);
        }
        
        ScanResult scan;
        
        try {
            scan = scanClient.scanImage(imageRef);
        } catch (Exception e) {
            return semanticQuestionService.answerQuestion(request);
        }
        
        if (scan == null || scan.findings() == null || scan.findings().isEmpty()) {
            return semanticQuestionService.answerQuestion(request);
        }
        
        Map<String, List<String>> packagesByCve = new LinkedHashMap<>();
        for (ScanFinding f : scan.findings()) {
            if (f == null || f.cveId() == null || f.cveId().isBlank()) continue;
            
            List<String> pkgs = f.packages() != null ? f.packages() : List.of();
            packagesByCve.computeIfAbsent(f.cveId(), id -> new ArrayList<>()).addAll(pkgs);
        }
        
        Set<String> allowedCves = packagesByCve.keySet();
        
        if (allowedCves.isEmpty()) {
            return semanticQuestionService.answerQuestion(request);
        }
        
        return semanticQuestionService.answerQuestionForImage(
                request,
                allowedCves,
                packagesByCve.entrySet().stream()
                        .collect(Collectors.toMap(
                                Map.Entry::getKey,
                                e -> List.copyOf(e.getValue()),
                                (a, b) -> a,
                                LinkedHashMap::new
                        ))
        );
    }
    
    @Override
    public QaClaimResponse judge(QaClaimRequest request) {
        String imageRef = request.imageRef();
        boolean hasImage = imageRef != null && !imageRef.isBlank();
        
        if (!hasImage) {
            return semanticClaimService.judgeClaim(request, null, null, null);
        }
        
        ScanResult scan;
        
        try {
            scan = scanClient.scanImage(imageRef);
        } catch (Exception e) {
            return semanticClaimService.judgeClaim(request, null, null, null);
        }
        
        if (scan == null || scan.findings() == null || scan.findings().isEmpty()) {
            return semanticClaimService.judgeClaim(request, null, null, null);
        }
        
        Map<String, List<String>> packagesByCve = new LinkedHashMap<>();
        for (ScanFinding f : scan.findings()) {
            if (f == null || f.cveId() == null || f.cveId().isBlank()) continue;
            
            List<String> pkgs = f.packages() != null ? f.packages() : List.of();
            packagesByCve.computeIfAbsent(f.cveId(), id -> new ArrayList<>()).addAll(pkgs);
        }
        
        Set<String> allowedCves = packagesByCve.keySet();
        
        if (allowedCves.isEmpty()) {
            return semanticClaimService.judgeClaim(request, null, null, null);
        }
        
        Map<String, List<String>> safePackagesByCve = packagesByCve.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> List.copyOf(e.getValue()),
                        (a, b) -> a,
                        LinkedHashMap::new
                ));
        
        return semanticClaimService.judgeClaim(request, allowedCves, safePackagesByCve, scan.findings());
        
    }
}
