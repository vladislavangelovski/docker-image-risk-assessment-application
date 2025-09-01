package com.finki.vladislavangelovski.scan_service.core;

public interface TrivyInvoker {
    TrivyOutput run(TrivyInvocationRequest request) throws ScannerException;
}
