package com.finki.vladislavangelovski.common.error;

import java.util.Collections;
import java.util.Map;

public class ServiceException extends RuntimeException {
  private final int status;
  private final ErrorCode errorCode;
  private final Map<String, Object> details;

  public ServiceException(int status, ErrorCode errorCode, String message) {
    this(status, errorCode, message, Collections.emptyMap(), null);
  }

  public ServiceException(
      int status, ErrorCode errorCode, String message, Map<String, Object> details) {
    this(status, errorCode, message, details, null);
  }

  public ServiceException(
      int status,
      ErrorCode errorCode,
      String message,
      Map<String, Object> details,
      Throwable cause) {
    super(message, cause);
    this.status = status;
    this.errorCode = errorCode;
    this.details = details == null ? Collections.emptyMap() : details;
  }

  public int getStatus() {
    return status;
  }

  public ErrorCode getErrorCode() {
    return errorCode;
  }

  public Map<String, Object> getDetails() {
    return details;
  }
}
