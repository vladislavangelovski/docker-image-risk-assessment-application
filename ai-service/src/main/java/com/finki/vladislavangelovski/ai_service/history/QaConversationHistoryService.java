package com.finki.vladislavangelovski.ai_service.history;

import com.finki.vladislavangelovski.common.dto.QaConversationHistoryItem;
import com.finki.vladislavangelovski.common.dto.QaQuestionRequest;
import com.finki.vladislavangelovski.common.dto.QaQuestionResponse;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class QaConversationHistoryService {
  private static final int DEFAULT_LIMIT = 20;
  private static final int MAX_LIMIT = 100;

  private final QaConversationRepository repository;

  public QaConversationHistoryService(QaConversationRepository repository) {
    this.repository = repository;
  }

  public String recordQuestion(
      QaUserContext userContext, QaQuestionRequest request, QaQuestionResponse response) {
    if (userContext == null || !StringUtils.hasText(userContext.userId())) {
      return null;
    }
    return repository.appendQuestionExchange(userContext, request, response);
  }

  public List<QaConversationHistoryItem> listHistory(
      QaUserContext userContext, String chatScopeId, Integer limit) {
    if (userContext == null || !StringUtils.hasText(userContext.userId())) {
      return List.of();
    }
    return repository.findRecentConversations(
        userContext.userId().trim(), normalizeScope(chatScopeId), normalizeLimit(limit));
  }

  public boolean deleteConversation(QaUserContext userContext, String conversationId) {
    if (userContext == null || !StringUtils.hasText(userContext.userId())) {
      return false;
    }
    return repository.deleteConversation(userContext.userId().trim(), conversationId);
  }

  private static int normalizeLimit(Integer limit) {
    if (limit == null || limit < 1) {
      return DEFAULT_LIMIT;
    }
    return Math.min(limit, MAX_LIMIT);
  }

  private static String normalizeScope(String chatScopeId) {
    if (!StringUtils.hasText(chatScopeId)) {
      return null;
    }
    return chatScopeId.trim();
  }
}
