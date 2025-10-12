package com.finki.vladislavangelovski.scan_service.core.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finki.vladislavangelovski.scan_service.api.dto.ScanResult;
import com.finki.vladislavangelovski.scan_service.api.dto.Severity;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcScanPersistence implements ScanPersistence {
    private static final String SQL_INSERT_SCAN = """
            INSERT INTO scan.scans (
                scan_id, image, digest, scanner_version, os_family, os_version, started_at, finished_at, 
                total, critical, high, medium, low, unknown, fix_available
            ) VALUES (
                :scan_id, :image, :digest, :scanner_version, :os_family, :os_version,
                :started_at, :finished_at,  :total, :critical, :high, :medium, :low,
                :unknown, :fix_available
            )
            """;

    private static final String SQL_INSERT_RAW = """
            INSERT INTO scan.scan_raw (scan_id, raw_json)
            VALUES (:scan_id, CAST(:raw_json AS JSONB))
            """;

    private static final String SQL_INSERT_FINDINGS = """
    INSERT INTO scan.scan_findings (
        scan_id,
        cve_id, package, installed_version, fixed_version,
        severity, severity_source,
        cvss_source, cvss_score, cvss_vector,
        source_target, ref_urls
    ) VALUES (
        :scan_id,
        :cve_id, :package, :installed_version, :fixed_version,
        :severity, :severity_source,
        :cvss_source, :cvss_score, :cvss_vector,
        :source_target, CAST(:ref_urls AS jsonb)
    )
    """;

    private static final String SQL_SELECT_SCAN = """
    SELECT scan_id, image, digest, scanner_version,
           started_at, finished_at,
           total, critical, high, medium, low, unknown, fix_available
    FROM scan.scans WHERE scan_id = :scan_id
    """;

    private static final String SQL_SELECT_FINDINGS = """
    SELECT cve_id, package, installed_version, fixed_version,
           severity, severity_source,
           cvss_source, cvss_score, cvss_vector,
           source_target, ref_urls
    FROM scan.scan_findings
    WHERE scan_id = :scan_id
    ORDER BY id
    """;

    private static final String SQL_SELECT_RAW = """
    SELECT raw_json::text
    FROM scan.scan_raw WHERE scan_id = :scan_id
    """;

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public JdbcScanPersistence(NamedParameterJdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public void save(UUID scanId, ScanResult normalized, String rawJson) {
        var summary = normalized.summary();
        var sev = summary.severity();
        var findings = normalized.findings();

        MapSqlParameterSource scanParams = new MapSqlParameterSource()
                .addValue("scan_id", scanId)
                .addValue("image", nz(normalized.image()))
                .addValue("digest", normalized.digest())
                .addValue("scanner_version", normalized.scannerVersion())
                .addValue("os_family", null)
                .addValue("os_version", null)
                .addValue("started_at", Timestamp.from(normalized.startedAt()))
                .addValue("finished_at", Timestamp.from(normalized.startedAt()))
                .addValue("total", summary.total())
                .addValue("critical", sevCount(sev, Severity.CRITICAL))
                .addValue("high",     sevCount(sev, Severity.HIGH))
                .addValue("medium",   sevCount(sev, Severity.MEDIUM))
                .addValue("low",      sevCount(sev, Severity.LOW))
                .addValue("unknown",  sevCount(sev, Severity.UNKNOWN))
                .addValue("fix_available", summary.fixAvailable());

        jdbc.update(SQL_INSERT_SCAN, scanParams);

        if (findings != null && !findings.isEmpty()) {
            var batch = new java.util.ArrayList<MapSqlParameterSource>(findings.size());
            for (var f : findings) {
                batch.add(mapFinding(scanId, f));
            }
            jdbc.batchUpdate(SQL_INSERT_FINDINGS, batch.toArray(MapSqlParameterSource[]::new));
        }

        MapSqlParameterSource rawParams = new MapSqlParameterSource()
                .addValue("scan_id", scanId)
                .addValue("raw_json", rawJson != null ? rawJson : "null");

        jdbc.update(SQL_INSERT_RAW, rawParams);
    }

    @Override
    public Optional<LoadedScan> find(UUID scanId, boolean includeRaw) {
        var params = new MapSqlParameterSource("scan_id", scanId);

        var scans = jdbc.query(SQL_SELECT_SCAN, params, (rs, rn) -> {
            var sev = new java.util.EnumMap<Severity, Integer>(Severity.class);
            sev.put(Severity.CRITICAL, rs.getInt("critical"));
            sev.put(Severity.HIGH,     rs.getInt("high"));
            sev.put(Severity.MEDIUM,   rs.getInt("medium"));
            sev.put(Severity.LOW,      rs.getInt("low"));
            sev.put(Severity.UNKNOWN,  rs.getInt("unknown"));

            var summary = new com.finki.vladislavangelovski.scan_service.api.dto.Summary(
                    rs.getInt("total"),
                    java.util.Map.copyOf(sev),
                    rs.getInt("fix_available")
            );

            return new Object[] {
                    rs.getObject("scan_id", java.util.UUID.class),
                    rs.getString("image"),
                    rs.getString("digest"),
                    rs.getString("scanner_version"),
                    rs.getTimestamp("started_at").toInstant(),
                    rs.getTimestamp("finished_at").toInstant(),
                    summary
            };
        });

        if (scans.isEmpty()) return Optional.empty();
        var row = scans.get(0);

        var findings = jdbc.query(SQL_SELECT_FINDINGS, params, (rs, rn) -> {
            var cvss =
                    rs.getString("cvss_source") != null || rs.getBigDecimal("cvss_score") != null || rs.getString("cvss_vector") != null
                            ? new com.finki.vladislavangelovski.scan_service.api.dto.Cvss(
                            rs.getString("cvss_source"),
                            rs.getBigDecimal("cvss_score"),
                            rs.getString("cvss_vector")
                    )
                            : null;

            java.util.List<String> refs;
            try {
                var json = rs.getString("ref_urls");
                refs = json == null ? java.util.List.of()
                        : mapper.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<java.util.List<String>>(){});
            } catch (Exception e) {
                refs = java.util.List.of();
            }

            var sev = parseSeverity(rs.getString("severity"));

            return new com.finki.vladislavangelovski.scan_service.api.dto.Finding(
                    rs.getString("cve_id"),
                    rs.getString("package"),
                    rs.getString("installed_version"),
                    rs.getString("fixed_version"),
                    sev,
                    rs.getString("severity_source"),
                    cvss,
                    refs,
                    rs.getString("source_target")
            );
        });

        String raw = null;
        if (includeRaw) {
            raw = jdbc.query(SQL_SELECT_RAW, params, (rs) -> rs.next() ? rs.getString(1) : null);
        }

        var scanResult = new com.finki.vladislavangelovski.scan_service.api.dto.ScanResult(
                (java.util.UUID) row[0],
                (String) row[1],
                (String) row[2],
                (String) row[3],
                (java.time.Instant) row[4],
                (java.time.Instant) row[5],
                (com.finki.vladislavangelovski.scan_service.api.dto.Summary) row[6],
                java.util.List.copyOf(findings)
        );

        return Optional.of(new LoadedScan(scanId, scanResult, raw));
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static int sevCount(Map<Severity, Integer> map, Severity key) {
        return map != null ? map.getOrDefault(key, 0) : 0;
    }

    private MapSqlParameterSource mapFinding(
            UUID scanId,
            com.finki.vladislavangelovski.scan_service.api.dto.Finding f) {

        var cvss = f.cvss(); // may be null

        // Serialize reference URLs list -> JSON string for JSONB column
        String refsJson;
        try {
            refsJson = mapper.writeValueAsString(
                    f.references() == null ? java.util.List.of() : f.references()   // if getters: f.getReferences()
            );
        } catch (Exception e) {
            refsJson = "[]";
        }

        return new MapSqlParameterSource()
                .addValue("scan_id", scanId)
                .addValue("cve_id", f.cveId())
                .addValue("package", f.pkg())
                .addValue("installed_version", f.installedVersion())
                .addValue("fixed_version", f.fixedVersion())
                .addValue("severity", f.severity() != null ? f.severity().name() : "UNKNOWN")
                .addValue("severity_source", f.severitySource())
                .addValue("cvss_source",  cvss != null ? cvss.source() : null)
                .addValue("cvss_score",   cvss != null ? cvss.score()  : null)
                .addValue("cvss_vector",  cvss != null ? cvss.vector() : null)
                .addValue("source_target", f.sourceTarget())
                .addValue("ref_urls", refsJson);
    }

    private static Severity parseSeverity(String s) {
        if (s == null) return Severity.UNKNOWN;
        try { return Severity.valueOf(s.trim().toUpperCase()); }
        catch (IllegalArgumentException ex) { return Severity.UNKNOWN; }
    }
}
