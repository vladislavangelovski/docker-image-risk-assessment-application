package com.finki.vladislavangelovski.ai_service.api;

import com.finki.vladislavangelovski.ai_service.history.QaConversationHistoryService;
import com.finki.vladislavangelovski.ai_service.history.QaUserContext;
import com.finki.vladislavangelovski.ai_service.history.QaUserContextResolver;
import com.finki.vladislavangelovski.common.dto.QaConversationHistoryItem;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/qa/history")
public class QaHistoryController {
  private final QaConversationHistoryService historyService;

  public QaHistoryController(QaConversationHistoryService historyService) {
    this.historyService = historyService;
  }

  @GetMapping
  public List<QaConversationHistoryItem> listHistory(
      @RequestHeader(value = "X-User-Id", required = false) String userId,
      @RequestHeader(value = "X-User-Name", required = false) String userName,
      @RequestHeader(value = "X-User-Email", required = false) String userEmail,
      @RequestHeader(value = "X-Chat-Session", required = false) String chatSessionId,
      @RequestParam(value = "chatScopeId", required = false) String chatScopeId,
      @RequestParam(value = "limit", required = false) Integer limit) {
    QaUserContext userContext =
        QaUserContextResolver.resolve(userId, userName, userEmail, chatSessionId);
    return historyService.listHistory(userContext, chatScopeId, limit);
  }

  @DeleteMapping("/{conversationId}")
  public void deleteConversation(
      @RequestHeader(value = "X-User-Id", required = false) String userId,
      @RequestHeader(value = "X-User-Name", required = false) String userName,
      @RequestHeader(value = "X-User-Email", required = false) String userEmail,
      @RequestHeader(value = "X-Chat-Session", required = false) String chatSessionId,
      @PathVariable("conversationId") String conversationId) {
    QaUserContext userContext =
        QaUserContextResolver.resolve(userId, userName, userEmail, chatSessionId);
    historyService.deleteConversation(userContext, conversationId);
  }
}
