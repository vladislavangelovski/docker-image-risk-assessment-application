package com.finki.vladislavangelovski.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record QaConversationHistoryItem(
    String conversationId,
    String chatScopeId,
    String title,
    String imageRef,
    Instant createdAt,
    Instant updatedAt,
    List<QaConversationMessage> messages) {
  public QaConversationHistoryItem {
    if (messages == null) {
      messages = List.of();
    } else {
      messages = messages.stream().filter(Objects::nonNull).toList();
    }
  }
}
