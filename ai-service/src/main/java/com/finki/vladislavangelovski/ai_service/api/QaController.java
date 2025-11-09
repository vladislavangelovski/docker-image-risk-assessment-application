package com.finki.vladislavangelovski.ai_service.api;

import com.finki.vladislavangelovski.ai_service.service.QaService;
import com.finki.vladislavangelovski.common.dto.QaClaimRequest;
import com.finki.vladislavangelovski.common.dto.QaClaimResponse;
import com.finki.vladislavangelovski.common.dto.QaQuestionRequest;
import com.finki.vladislavangelovski.common.dto.QaQuestionResponse;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/qa")
public class QaController {
    private final QaService qaService;
    
    public QaController(QaService qaService) {
        this.qaService = qaService;
    }
    
    @PostMapping("/question")
    public QaQuestionResponse ask(@RequestBody QaQuestionRequest request) {
        if (request == null || !StringUtils.hasText(request.question())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "question is required");
        }
        
        Integer k = request.k() != null ? request.k() : 4;
        return qaService.ask(new QaQuestionRequest(request.question(), request.imageRef(), k));
    }
    
    @PostMapping("/claim")
    public QaClaimResponse judge(@RequestBody QaClaimRequest request) {
        if (request == null || !StringUtils.hasText(request.claim())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "claim is required");
        }
        return qaService.judge(request);
    }
}
