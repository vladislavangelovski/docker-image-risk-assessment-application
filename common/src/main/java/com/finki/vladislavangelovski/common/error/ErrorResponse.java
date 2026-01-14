package com.finki.vladislavangelovski.common.error;

import java.time.Instant;
import java.util.Map;

public record ErrorResponse(
    Instant timestamp,
    String requestId,
    int status,
    String message,
    String path,
    Map<String, Object> details) {
  public static ErrorResponse of(
      int status, String message, String path, String requestId, Map<String, Object> details) {
    return new ErrorResponse(Instant.now(), requestId, status, message, path, details);
  }
}
