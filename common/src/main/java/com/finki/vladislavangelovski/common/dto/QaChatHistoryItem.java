package com.finki.vladislavangelovski.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record QaChatHistoryItem(
    Long id,
    QaChatKind kind,
    String prompt,
    String imageRef,
    Integer k,
    QaQuestionResponse questionResponse,
    QaClaimResponse claimResponse,
    Instant createdAt) {}
