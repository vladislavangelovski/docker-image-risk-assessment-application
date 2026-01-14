package com.finki.vladislavangelovski.scan_service.api.error;

import com.finki.vladislavangelovski.scan_service.core.ParserException;
import com.finki.vladislavangelovski.scan_service.core.ScanCache;
import com.finki.vladislavangelovski.scan_service.core.ScanJobStore;
import com.finki.vladislavangelovski.scan_service.core.ScannerException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class GlobalExceptionHandler {
  private static ResponseEntity<ErrorResponse> build(
      HttpStatus status, ErrorCode code, String message, Map<String, Object> details) {
    return ResponseEntity.status(status).body(new ErrorResponse(code, message, details));
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
    return build(HttpStatus.BAD_REQUEST, ErrorCode.BAD_REQUEST_IMAGE, ex.getMessage(), Map.of());
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException ex) {
    return build(
        HttpStatus.BAD_REQUEST, ErrorCode.BAD_REQUEST_IMAGE, "Malformed JSON request", Map.of());
  }

  @ExceptionHandler(NotFoundException.class)
  public ResponseEntity<ErrorResponse> handleNotFound(NotFoundException ex) {
    return build(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND, ex.getMessage(), Map.of());
  }

  @ExceptionHandler(ParserException.class)
  public ResponseEntity<ErrorResponse> handleParser(ParserException ex) {
    return build(
        HttpStatus.UNPROCESSABLE_ENTITY, ErrorCode.TRIVY_PARSE_ERROR, ex.getMessage(), Map.of());
  }

  @ExceptionHandler(ScanCache.CacheWriteException.class)
  public ResponseEntity<ErrorResponse> handleCache(ScanCache.CacheWriteException ex) {
    return build(
        HttpStatus.INTERNAL_SERVER_ERROR,
        ErrorCode.CACHE_WRITE_ERROR,
        "Failed to cache scan result",
        Map.of());
  }

  @ExceptionHandler(ScanJobStore.StoreWriteException.class)
  public ResponseEntity<ErrorResponse> handleJobStore(ScanJobStore.StoreWriteException ex) {
    return build(
        HttpStatus.INTERNAL_SERVER_ERROR,
        ErrorCode.JOB_STORE_ERROR,
        "Failed to persist scan job status",
        Map.of());
  }

  @ExceptionHandler(ScannerException.class)
  public ResponseEntity<ErrorResponse> handleScanner(ScannerException ex) {
    return build(HttpStatus.BAD_GATEWAY, ErrorCode.TRIVY_SCAN_ERROR, ex.getMessage(), Map.of());
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorResponse> handleAny(Exception ex) {
    return build(
        HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL, "Unexpected server error", Map.of());
  }
}
