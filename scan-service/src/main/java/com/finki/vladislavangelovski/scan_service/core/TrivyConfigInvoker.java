package com.finki.vladislavangelovski.scan_service.core;

public interface TrivyConfigInvoker {
  TrivyConfigOutput run(TrivyConfigInvocationRequest request) throws ScannerException;
}
