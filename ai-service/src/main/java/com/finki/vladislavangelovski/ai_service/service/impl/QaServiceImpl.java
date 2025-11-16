package com.finki.vladislavangelovski.ai_service.service.impl;

import com.finki.vladislavangelovski.ai_service.qa.SemanticQuestionService;
import com.finki.vladislavangelovski.ai_service.service.QaService;
import com.finki.vladislavangelovski.common.dto.*;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class QaServiceImpl implements QaService {
    
    private final SemanticQuestionService semanticQuestionService;
    
    public QaServiceImpl(SemanticQuestionService semanticQuestionService) {
        this.semanticQuestionService = semanticQuestionService;
    }
    
    @Override
    public QaQuestionResponse ask(QaQuestionRequest request) {
        return semanticQuestionService.answer(request);
    }
    
    @Override
    public QaClaimResponse judge(QaClaimRequest request) {
        List<Citation> citations = List.of(
                new Citation("CVE-2021-44228", "https://nvd.nist.gov/vuln/detail/CVE-2021-44228",
                             "Log4j RCE (Log4Shell)"));
        return new QaClaimResponse(Verdict.INSUFFICIENT,
                                   "Stub verdict: insufficient evidence to confirm or deny the claim.", citations);
    }
}
