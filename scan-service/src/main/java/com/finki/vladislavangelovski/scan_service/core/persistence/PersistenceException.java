package com.finki.vladislavangelovski.scan_service.core.persistence;

public class PersistenceException extends RuntimeException {
  public PersistenceException(String message) {
    super(message);
  }

  public PersistenceException(String message, Throwable cause) {
    super(message, cause);
  }
}
