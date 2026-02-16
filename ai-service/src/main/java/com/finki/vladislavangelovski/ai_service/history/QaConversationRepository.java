package com.finki.vladislavangelovski.ai_service.history;

import com.finki.vladislavangelovski.common.dto.QaConversationHistoryItem;
import com.finki.vladislavangelovski.common.dto.QaQuestionRequest;
import com.finki.vladislavangelovski.common.dto.QaQuestionResponse;
import java.util.List;

public interface QaConversationRepository {
  String appendQuestionExchange(
      QaUserContext userContext, QaQuestionRequest request, QaQuestionResponse response);

  List<QaConversationHistoryItem> findRecentConversations(
      String userId, String chatScopeId, int limit);

  boolean deleteConversation(String userId, String conversationId);
}
