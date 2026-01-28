package com.finki.vladislavangelovski.ai_service.history;

import com.finki.vladislavangelovski.common.dto.QaChatHistoryItem;
import com.finki.vladislavangelovski.common.dto.QaClaimRequest;
import com.finki.vladislavangelovski.common.dto.QaClaimResponse;
import com.finki.vladislavangelovski.common.dto.QaQuestionRequest;
import com.finki.vladislavangelovski.common.dto.QaQuestionResponse;
import java.util.List;

public interface QaChatHistoryRepository {
  void saveQuestion(
      String userId, String userName, QaQuestionRequest request, QaQuestionResponse response);

  void saveClaim(String userId, String userName, QaClaimRequest request, QaClaimResponse response);

  List<QaChatHistoryItem> findRecentByUser(String userId, int limit);
}
