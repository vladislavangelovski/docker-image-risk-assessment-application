package com.finki.vladislavangelovski.ai_service.service;

import com.finki.vladislavangelovski.common.dto.QaClaimRequest;
import com.finki.vladislavangelovski.common.dto.QaClaimResponse;
import com.finki.vladislavangelovski.common.dto.QaQuestionRequest;
import com.finki.vladislavangelovski.common.dto.QaQuestionResponse;

public interface QaService {
    QaQuestionResponse ask(QaQuestionRequest request);
    
    QaClaimResponse judge(QaClaimRequest request);
}
