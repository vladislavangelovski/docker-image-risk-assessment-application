package com.finki.vladislavangelovski.ai_service.history;

import org.springframework.util.StringUtils;

public final class QaUserContextResolver {
  private QaUserContextResolver() {}

  public static QaUserContext resolve(
      String userId, String userName, String userEmail, String chatSessionId) {
    String resolvedUserId = firstNonBlank(userId, userEmail, userName);
    if (!StringUtils.hasText(resolvedUserId) && StringUtils.hasText(chatSessionId)) {
      resolvedUserId = "session:" + chatSessionId.trim();
    }
    if (!StringUtils.hasText(resolvedUserId)) {
      return null;
    }

    String resolvedName = firstNonBlank(userName, userEmail);
    return new QaUserContext(
        resolvedUserId.trim(), StringUtils.hasText(resolvedName) ? resolvedName.trim() : null);
  }

  private static String firstNonBlank(String... values) {
    if (values == null) {
      return null;
    }
    for (String value : values) {
      if (StringUtils.hasText(value)) {
        return value;
      }
    }
    return null;
  }
}
