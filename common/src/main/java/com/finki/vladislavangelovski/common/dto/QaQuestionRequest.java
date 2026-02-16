package com.finki.vladislavangelovski.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record QaQuestionRequest(
    String question,
    String imageRef,
    Integer k,
    String assessmentContext,
    List<QaChatTurn> chatHistory,
    String chatScopeId,
    String conversationId) {
  public QaQuestionRequest {
    if (chatHistory == null) {
      chatHistory = List.of();
    } else {
      chatHistory = chatHistory.stream().filter(Objects::nonNull).toList();
    }
  }

  public QaQuestionRequest(String question, String imageRef, Integer k) {
    this(question, imageRef, k, null, List.of(), null, null);
  }

  public QaQuestionRequest(
      String question,
      String imageRef,
      Integer k,
      String assessmentContext,
      List<QaChatTurn> chatHistory) {
    this(question, imageRef, k, assessmentContext, chatHistory, null, null);
  }
}
