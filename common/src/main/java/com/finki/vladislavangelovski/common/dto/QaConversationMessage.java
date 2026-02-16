package com.finki.vladislavangelovski.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record QaConversationMessage(
    Long id, String role, String content, List<Citation> citations, Instant createdAt) {
  public QaConversationMessage {
    if (citations == null) {
      citations = List.of();
    } else {
      citations = citations.stream().filter(Objects::nonNull).toList();
    }
  }
}
