package com.finki.vladislavangelovski.scan_service.api;

import com.finki.vladislavangelovski.scan_service.api.dto.ScanRequest;
import com.finki.vladislavangelovski.scan_service.api.dto.ScanResult;
import com.finki.vladislavangelovski.scan_service.api.error.NotFoundException;
import com.finki.vladislavangelovski.scan_service.core.ParserException;
import com.finki.vladislavangelovski.scan_service.core.ScanCache;
import com.finki.vladislavangelovski.scan_service.core.ScanOrchestrator;
import com.finki.vladislavangelovski.scan_service.core.ScannerException;
import com.finki.vladislavangelovski.scan_service.core.config.ScanProperties;
import com.finki.vladislavangelovski.scan_service.core.persistence.ScanPersistence;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


import java.time.Duration;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/scans")
@Tag(name = "Image Scans", description = "Submit and retrieve container image vulnerability scans")
public class ScanController {
    private final ScanOrchestrator orchestrator;
    private final ScanCache cache;
    private final ScanPersistence persistence;
    private final ScanProperties properties;
    private static final Logger log = LoggerFactory.getLogger(ScanController.class);

    public ScanController(ScanOrchestrator orchestrator, ScanCache cache, ScanPersistence persistence,  ScanProperties properties) {
        this.orchestrator = orchestrator;
        this.cache = cache;
        this.persistence = persistence;
        this.properties = properties;
    }

    /**
     * POST /api/v1/scans  (sync MVP)
     * - Validates request (image required; options within allowed ranges)
     * - Delegates to orchestrator (to be wired next step)
     * - Returns normalized ScanResult (no raw here)
     */
    @PostMapping
    @Operation(
            summary = "Scan an image",
            description = "Invokes Trivy and returns a normalized vulnerability report."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Scan completed",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ScanResult.class),
                            examples = @ExampleObject(name = "ok", value = """
            {
              "scanId": "c9b3a6e3-5d7e-4a06-9c7a-0b2a9d347e77",
              "image": "nginx:1.25",
              "digest": "sha256:…",
              "scannerVersion": "Trivy 0.67.2",
              "startedAt": "2025-10-12T09:04:35Z",
              "finishedAt": "2025-10-12T09:04:36Z",
              "summary": {
                "total": 12,
                "severity": {"CRITICAL":1,"HIGH":3,"MEDIUM":5,"LOW":3,"UNKNOWN":0},
                "fixAvailable": 6
              },
              "findings": [
                {
                  "cveId":"CVE-2024-XXXX",
                  "package":"openssl",
                  "installedVersion":"1.1.1u-1",
                  "fixedVersion":"1.1.1v-1",
                  "severity":"HIGH",
                  "severitySource":"nvd",
                  "cvss":{"source":"nvd","score":7.5,"vector":"CVSS:3.1/..."},
                  "references":["https://nvd.nist.gov/vuln/detail/CVE-2024-XXXX"],
                  "sourceTarget":"alpine:3.19 (os)"
                }
              ]
            }
            """)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Bad request",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
            {"errorCode":"BAD_REQUEST_IMAGE","message":"image is required","details":{}}
            """)
                    )
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Unexpected error",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
            {"errorCode":"INTERNAL","message":"Unexpected server error","details":{}}
            """)
                    )
            )
    })
    @RequestBody(
            required = true,
            description = "Image to scan and optional registry credentials.",
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ScanRequest.class),
                    examples = @ExampleObject(name = "request", value = """
        {
          "image": "nginx:1.25",
          "registryCreds": { "username": "user", "password": "pass" },
          "options": { "timeoutSec": 120, "ignoreUnfixed": true, "scanners": ["vuln"] }
        }
        """)
            )
    )
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
    @Operation(
            summary = "Get a scan by ID",
            description = "Returns the normalized scan by default; pass `raw=true` to get the raw Trivy JSON."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Normalized result (default)",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ScanResult.class),
                            examples = @ExampleObject(name = "normalized", value = """
            {
              "scanId": "c9b3a6e3-5d7e-4a06-9c7a-0b2a9d347e77",
              "image": "nginx:1.25",
              "scannerVersion": "Trivy 0.67.2",
              "summary": {
                "total": 12,
                "severity": {"CRITICAL":1,"HIGH":3,"MEDIUM":5,"LOW":3,"UNKNOWN":0},
                "fixAvailable": 6
              },
              "findings": [ { "cveId":"CVE-2024-XXXX", "package":"openssl", "severity":"HIGH" } ]
            }
            """)
                    )
            ),
            @ApiResponse(
                    responseCode = "200",
                    description = "Raw Trivy JSON when `raw=true`",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = Object.class),
                            examples = @ExampleObject(name = "raw", value = """
            {"ArtifactName":"nginx:1.25","Results":[{"Target":"alpine:3.19","Vulnerabilities":[{"VulnerabilityID":"CVE-2024-XXXX","PkgName":"openssl","Severity":"HIGH"}]}]}
            """)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Not found",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
            {"errorCode":"NOT_FOUND","message":"Scan not found or expired: <uuid>","details":{}}
            """)
                    )
            )
    })
    public ResponseEntity<?> get(
            @PathVariable("scanId") UUID scanId,
            @RequestParam(name = "raw", required = false, defaultValue = "false") boolean raw
    ) {
        // 1) Try Redis
        var cachedOpt = cache.get(scanId);
        if (cachedOpt.isPresent()) {
            var cached = cachedOpt.get();
            if (raw) {
                return ResponseEntity.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(cached.rawJson());
            }
            return ResponseEntity.ok(cached.normalized());
        }

        // 2) Fallback to DB
        var loadedOpt = persistence.find(scanId, raw);
        if (loadedOpt.isPresent()) {
            var loaded = loadedOpt.get();
            try {
                var ttl = Duration.ofSeconds(properties.getCache().getTtlSeconds());
                cache.put(
                        scanId,
                        loaded.normalized(),
                        loaded.rawJsonOptional().orElse(null),
                        ttl
                );
            } catch (ScanCache.CacheWriteException e) {
                log.warn("[scan-service] Cache put failed on DB fallback for scanId={}", scanId, e);
            }
            if (raw) {
                return ResponseEntity.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(loaded.rawJsonOptional().orElse("{}"));
            }
            return ResponseEntity.ok(loaded.normalized());
        }
        throw new NotFoundException("Scan not found or expired: " + scanId);
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
