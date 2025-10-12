-- Create a dedicated schema for scan-service (safe if already present)
CREATE SCHEMA IF NOT EXISTS scan;

-- ========== Summary table ==========
CREATE TABLE IF NOT EXISTS scan.scans (
                                          scan_id         UUID        PRIMARY KEY,
                                          image           TEXT        NOT NULL,             -- e.g., "nginx:1.25"
                                          digest          TEXT,                              -- prefer repo digest; fallback to ImageID
                                          scanner_version TEXT,                              -- e.g., "Trivy 0.67.2"
                                          os_family       TEXT,                              -- optional: e.g., "debian"
                                          os_version      TEXT,                              -- optional: e.g., "12.5"
                                          started_at      TIMESTAMPTZ NOT NULL,
                                          finished_at     TIMESTAMPTZ NOT NULL,

    -- rollup counts
                                          total           INT         NOT NULL CHECK (total >= 0),
    critical        INT         NOT NULL CHECK (critical >= 0),
    high            INT         NOT NULL CHECK (high >= 0),
    medium          INT         NOT NULL CHECK (medium >= 0),
    low             INT         NOT NULL CHECK (low >= 0),
    unknown         INT         NOT NULL CHECK (unknown >= 0),
    fix_available   INT         NOT NULL CHECK (fix_available >= 0)
    );

-- Helpful indexes for listing/browsing
CREATE INDEX IF NOT EXISTS idx_scans_started_at  ON scan.scans (started_at DESC);
CREATE INDEX IF NOT EXISTS idx_scans_image       ON scan.scans (image);
CREATE INDEX IF NOT EXISTS idx_scans_digest      ON scan.scans (digest);

-- ========== Findings table ==========
CREATE TABLE IF NOT EXISTS scan.scan_findings (
                                                  id               BIGSERIAL  PRIMARY KEY,          -- surrogate key for easy paging
                                                  scan_id          UUID       NOT NULL REFERENCES scan.scans(scan_id) ON DELETE CASCADE,

    cve_id           TEXT       NOT NULL,             -- CVE-… or GHSA-…
    package          TEXT,
    installed_version TEXT,
    fixed_version    TEXT,

    severity         TEXT       NOT NULL,             -- CRITICAL|HIGH|MEDIUM|LOW|UNKNOWN
    severity_source  TEXT,

    cvss_source      TEXT,                            -- "nvd", "redhat", …
    cvss_score       NUMERIC(3,1),                    -- e.g., 9.8
    cvss_vector      TEXT,

    source_target    TEXT,                            -- Trivy "Target"
    ref_urls       JSONB      NOT NULL DEFAULT '[]'::jsonb   -- array of URLs
    );

-- Targeted indexes for common queries
CREATE INDEX IF NOT EXISTS idx_findings_scan      ON scan.scan_findings (scan_id);
CREATE INDEX IF NOT EXISTS idx_findings_cve       ON scan.scan_findings (cve_id);
CREATE INDEX IF NOT EXISTS idx_findings_pkg       ON scan.scan_findings (package);
CREATE INDEX IF NOT EXISTS idx_findings_severity  ON scan.scan_findings (severity);
CREATE INDEX IF NOT EXISTS idx_findings_refs_gin  ON scan.scan_findings USING GIN (ref_urls);

-- ========== Raw JSON table ==========
CREATE TABLE IF NOT EXISTS scan.scan_raw (
                                             scan_id   UUID   PRIMARY KEY REFERENCES scan.scans(scan_id) ON DELETE CASCADE,
    raw_json  JSONB  NOT NULL
    );
