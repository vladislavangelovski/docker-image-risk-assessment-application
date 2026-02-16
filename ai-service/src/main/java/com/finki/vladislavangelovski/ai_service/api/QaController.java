package com.finki.vladislavangelovski.ai_service.api;

import com.finki.vladislavangelovski.ai_service.history.QaUserContext;
import com.finki.vladislavangelovski.ai_service.history.QaUserContextResolver;
import com.finki.vladislavangelovski.ai_service.service.QaService;
import com.finki.vladislavangelovski.common.dto.QaQuestionRequest;
import com.finki.vladislavangelovski.common.dto.QaQuestionResponse;
import java.util.Locale;
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

  public QaController(QaService qaService) {
    this.qaService = qaService;
  }

  @PostMapping("/question")
  public QaQuestionResponse answerQuestion(
      @RequestBody QaQuestionRequest request,
      @RequestHeader(value = "X-User-Id", required = false) String userId,
      @RequestHeader(value = "X-User-Name", required = false) String userName,
      @RequestHeader(value = "X-User-Email", required = false) String userEmail,
      @RequestHeader(value = "X-Chat-Session", required = false) String chatSessionId) {
    if (request == null || !StringUtils.hasText(request.question())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "question is required");
    }

    Integer k = request.k() != null ? request.k() : 4;
    String chatScopeId = normalizeChatScopeId(request.chatScopeId(), request.imageRef());
    QaQuestionRequest normalized =
        new QaQuestionRequest(
            request.question(),
            request.imageRef(),
            k,
            request.assessmentContext(),
            request.chatHistory(),
            chatScopeId,
            request.conversationId());
    QaUserContext userContext =
        QaUserContextResolver.resolve(userId, userName, userEmail, chatSessionId);
    return qaService.answerQuestion(normalized, userContext);
  }

  private static String normalizeChatScopeId(String chatScopeId, String imageRef) {
    if (StringUtils.hasText(chatScopeId)) {
      return chatScopeId.trim();
    }
    if (StringUtils.hasText(imageRef)) {
      return "image|" + imageRef.trim().toLowerCase(Locale.ROOT);
    }
    return "assessment|default";
  }
}
