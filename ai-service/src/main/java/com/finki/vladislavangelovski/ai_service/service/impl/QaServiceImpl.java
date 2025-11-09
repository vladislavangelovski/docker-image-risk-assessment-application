package com.finki.vladislavangelovski.ai_service.service.impl;

import com.finki.vladislavangelovski.ai_service.service.QaService;
import com.finki.vladislavangelovski.common.dto.*;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class QaServiceImpl implements QaService {
    @Override
    public QaQuestionResponse ask(QaQuestionRequest request) {
        List<Citation> citations = List.of(
                new Citation("CVE-2021-44228", "https://nvd.nist.gov/vuln/detail/CVE-2021-44228",
                             "Log4j RCE (Log4Shell)"));
        return new QaQuestionResponse(
                "Stub answer: This image appears affected by Log4Shell. Upgrade Log4j and rebuild.", citations,
                List.of("log4j-core:2.14.1"));
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
