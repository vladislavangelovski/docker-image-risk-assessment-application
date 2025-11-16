package com.finki.vladislavangelovski.ai_service.api;

import com.finki.vladislavangelovski.ai_service.qa.SemanticQuestionService;
import com.finki.vladislavangelovski.common.dto.QaQuestionRequest;
import com.finki.vladislavangelovski.common.dto.QaQuestionResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@RestController
@RequestMapping("/api/semantic")
public class SemanticQaController {
    private final SemanticQuestionService semanticQuestionService;
    
    public SemanticQaController(SemanticQuestionService semanticQuestionService) {
        this.semanticQuestionService = semanticQuestionService;
    }
    
    @PostMapping(path = "/qa", consumes = APPLICATION_JSON_VALUE, produces = APPLICATION_JSON_VALUE)
    public QaQuestionResponse ask(@RequestBody QaQuestionRequest request) {
        return semanticQuestionService.answer(request);
    }
}
