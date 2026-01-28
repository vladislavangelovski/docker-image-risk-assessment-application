package com.finki.vladislavangelovski.ai_service.history;

import com.finki.vladislavangelovski.common.dto.QaChatHistoryItem;
import com.finki.vladislavangelovski.common.dto.QaClaimRequest;
import com.finki.vladislavangelovski.common.dto.QaClaimResponse;
import com.finki.vladislavangelovski.common.dto.QaQuestionRequest;
import com.finki.vladislavangelovski.common.dto.QaQuestionResponse;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class QaChatHistoryService {
  private static final int DEFAULT_LIMIT = 50;
  private static final int MAX_LIMIT = 200;

  private final QaChatHistoryRepository repository;

  public QaChatHistoryService(QaChatHistoryRepository repository) {
    this.repository = repository;
  }

  public void recordQuestion(
      String userId,
      String userName,
      String userEmail,
      QaQuestionRequest request,
      QaQuestionResponse response) {
    String resolvedUserId = resolveUserId(userId, userName, userEmail);
    if (!StringUtils.hasText(resolvedUserId)) {
      return;
    }
    String resolvedName = resolveUserName(userName, userEmail);
    repository.saveQuestion(resolvedUserId, resolvedName, request, response);
  }

  public void recordClaim(
      String userId,
      String userName,
      String userEmail,
      QaClaimRequest request,
      QaClaimResponse response) {
    String resolvedUserId = resolveUserId(userId, userName, userEmail);
    if (!StringUtils.hasText(resolvedUserId)) {
      return;
    }
    String resolvedName = resolveUserName(userName, userEmail);
    repository.saveClaim(resolvedUserId, resolvedName, request, response);
  }

  public List<QaChatHistoryItem> listHistory(
      String userId, String userName, String userEmail, Integer limit) {
    String resolvedUserId = resolveUserId(userId, userName, userEmail);
    if (!StringUtils.hasText(resolvedUserId)) {
      return List.of();
    }
    int safeLimit = normalizeLimit(limit);
    return repository.findRecentByUser(resolvedUserId, safeLimit);
  }

  private static String resolveUserId(String userId, String userName, String userEmail) {
    if (StringUtils.hasText(userId)) {
      return userId.trim();
    }
    if (StringUtils.hasText(userName)) {
      return userName.trim();
    }
    if (StringUtils.hasText(userEmail)) {
      return userEmail.trim();
    }
    return null;
  }

  private static String resolveUserName(String userName, String userEmail) {
    if (StringUtils.hasText(userName)) {
      return userName.trim();
    }
    if (StringUtils.hasText(userEmail)) {
      return userEmail.trim();
    }
    return null;
  }

  private static int normalizeLimit(Integer limit) {
    if (limit == null) {
      return DEFAULT_LIMIT;
    }
    int value = limit;
    if (value < 1) {
      return DEFAULT_LIMIT;
    }
    return Math.min(value, MAX_LIMIT);
  }
}
