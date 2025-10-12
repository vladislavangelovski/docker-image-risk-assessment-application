package com.finki.vladislavangelovski.scan_service.core;

import com.finki.vladislavangelovski.scan_service.api.dto.ScanRequest;
import com.finki.vladislavangelovski.scan_service.api.dto.ScanResult;

import java.util.UUID;

public interface ScanOrchestrator {
    ScanResult scan(ScanRequest request) throws  ScannerException, ParserException, ScanCache.CacheWriteException;

    boolean exists(UUID scanId);
}
