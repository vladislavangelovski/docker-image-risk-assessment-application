package com.finki.vladislavangelovski.scan_service.api;

import com.finki.vladislavangelovski.scan_service.api.dto.ScanRequest;
import com.finki.vladislavangelovski.scan_service.api.dto.ScanResult;
import com.finki.vladislavangelovski.scan_service.api.error.NotFoundException;
import com.finki.vladislavangelovski.scan_service.core.ParserException;
import com.finki.vladislavangelovski.scan_service.core.ScanCache;
import com.finki.vladislavangelovski.scan_service.core.ScanOrchestrator;
import com.finki.vladislavangelovski.scan_service.core.ScannerException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


import java.util.UUID;

@RestController
@RequestMapping("/api/v1/scans")
@RequiredArgsConstructor
public class ScanController {
    private final ScanOrchestrator orchestrator;
    private final ScanCache cache;

    /**
     * POST /api/v1/scans  (sync MVP)
     * - Validates request (image required; options within allowed ranges)
     * - Delegates to orchestrator (to be wired next step)
     * - Returns normalized ScanResult (no raw here)
     */
    @PostMapping
    public ResponseEntity<ScanResult> create(@RequestBody ScanRequest request) throws ScannerException, ParserException, ScanCache.CacheWriteException {
        validate(request);
        ScanResult result = orchestrator.scan(request);
        return ResponseEntity.ok(result);
    }

    /**
     * GET /api/v1/scans/{scanId}?raw=true|false
     * - raw=true => return verbatim Trivy JSON
     * - default => return normalized result
     */
    @GetMapping("{scanId}")
    public ResponseEntity<?> get(
            @PathVariable UUID scanId,
            @RequestParam(name = "raw", required = false, defaultValue = "false") boolean raw
    ) {
        return cache.get(scanId).
                map(
                        cachedScan -> {
                            if (raw) {
                                return ResponseEntity.ok()
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .body(cachedScan.rawJson());
                            } else {
                                return ResponseEntity.ok(cachedScan.normalized());
                            }
                        }
                ).orElseThrow(() -> new NotFoundException("Scan not found or expired: " + scanId));
    }

    /** Minimal, temporary validation (we'll replace with a proper validator/handler) */
    private static void validate(ScanRequest req) {
        if (req == null || req.image() == null || req.image().isBlank()) {
            throw new IllegalArgumentException("image is required");
        }
        if (req.registryCreds() != null) {
            if (req.registryCreds().username() == null || req.registryCreds().username().isBlank()) {
                throw new IllegalArgumentException("registryCreds.username must be non-empty when provided");
            }
            if (req.registryCreds().password() == null || req.registryCreds().password().isBlank()) {
                throw new IllegalArgumentException("registryCreds.password must be non-empty when provided");
            }
        }
        if (req.options() != null) {
            Integer t = req.options().timeoutSec();
            if (t != null && (t < 10 || t > 900)) {
                throw new IllegalArgumentException("options.timeoutSec must be between 10 and 900");
            }
            if (req.options().scanners() != null && !req.options().scanners().equals(java.util.List.of("vuln"))) {
                throw new IllegalArgumentException("options.scanners must be [\"vuln\"] for MVP");
            }
        }
    }
}
