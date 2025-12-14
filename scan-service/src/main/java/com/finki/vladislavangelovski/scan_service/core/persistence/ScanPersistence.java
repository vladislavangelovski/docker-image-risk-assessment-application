package com.finki.vladislavangelovski.scan_service.core.persistence;

import com.finki.vladislavangelovski.scan_service.api.dto.ScanResult;

import java.util.Optional;
import java.util.UUID;

/**
 * Persistence contract for storing and loading scan results.
 * Implementations must persist in a single transaction:
 * - summary row (scans)
 * - findings (scan_findings)
 * - raw JSON (scan_raw)
 */
public interface ScanPersistence {
    /**
     * Persist one scan (summary + findings + raw JSON) atomically.
     *
     * @param scanId     unique scan id (same id we expose via API)
     * @param normalized normalized, parsed result we return from the API
     * @param rawJson    raw Trivy JSON (may be large; store as JSONB)
     */
    void save(UUID scanId,
              ScanResult normalized,
              String rawJson);
    
    /**
     * Load a scan by id. When includeRaw is true, rawJson is populated.
     *
     * @param scanId     target id
     * @param includeRaw whether to fetch raw JSON
     *
     * @return Optional present if found; empty if not found
     */
    Optional<LoadedScan> find(UUID scanId,
                              boolean includeRaw);

    /**
     * Load the most recent scan for a given image reference.
     *
     * @param image      image reference
     * @param includeRaw whether to fetch raw JSON
     * @return Optional present if found; empty if not found
     */
    Optional<LoadedScan> findLatestByImage(String image,
                                           boolean includeRaw);
}
