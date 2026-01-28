package com.finki.vladislavangelovski.ai_service.api;

import com.finki.vladislavangelovski.ai_service.history.QaChatHistoryService;
import com.finki.vladislavangelovski.ai_service.service.QaService;
import com.finki.vladislavangelovski.common.dto.QaClaimRequest;
import com.finki.vladislavangelovski.common.dto.QaClaimResponse;
import com.finki.vladislavangelovski.common.dto.QaQuestionRequest;
import com.finki.vladislavangelovski.common.dto.QaQuestionResponse;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/qa")
public class QaController {
  private final QaService qaService;
  private final QaChatHistoryService historyService;

  public QaController(QaService qaService, QaChatHistoryService historyService) {
    this.qaService = qaService;
    this.historyService = historyService;
  }

  @PostMapping("/question")
  public QaQuestionResponse answerQuestion(
      @RequestBody QaQuestionRequest request,
      @RequestHeader(value = "X-User-Id", required = false) String userId,
      @RequestHeader(value = "X-User-Name", required = false) String userName,
      @RequestHeader(value = "X-User-Email", required = false) String userEmail) {
    if (request == null || !StringUtils.hasText(request.question())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "question is required");
    }

    Integer k = request.k() != null ? request.k() : 4;
    QaQuestionRequest normalized = new QaQuestionRequest(request.question(), request.imageRef(), k);
    QaQuestionResponse response = qaService.answerQuestion(normalized);
    historyService.recordQuestion(userId, userName, userEmail, normalized, response);
    return response;
  }

  @PostMapping("/claim")
  public QaClaimResponse judge(
      @RequestBody QaClaimRequest request,
      @RequestHeader(value = "X-User-Id", required = false) String userId,
      @RequestHeader(value = "X-User-Name", required = false) String userName,
      @RequestHeader(value = "X-User-Email", required = false) String userEmail) {
    if (request == null || !StringUtils.hasText(request.claim())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "claim is required");
    }
    QaClaimResponse response = qaService.judge(request);
    historyService.recordClaim(userId, userName, userEmail, request, response);
    return response;
  }
}
