package com.finki.vladislavangelovski.scan_service.core.impl;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finki.vladislavangelovski.scan_service.api.dto.Cvss;
import com.finki.vladislavangelovski.scan_service.api.dto.Finding;
import com.finki.vladislavangelovski.scan_service.api.dto.Severity;
import com.finki.vladislavangelovski.scan_service.core.ParserException;
import com.finki.vladislavangelovski.scan_service.core.TrivyParser;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

public class JacksonTrivyParser implements TrivyParser {

    private final ObjectMapper mapper;

    public JacksonTrivyParser() {
        this.mapper = new ObjectMapper(new JsonFactory());
    }

    @Override
    public ParsedScan parse(String rawJson) throws ParserException {
        if (rawJson == null || rawJson.isBlank()) {
            throw new ParserException("Empty scanner output");
        }

        final JsonNode root;
        try {
            root = mapper.readTree(rawJson);
        } catch (Exception e) {
            throw new ParserException("Scanner output is not valid JSON", e);
        }

        // Attempt to read image/digest from common locations
        String imageName = optText(root, "ArtifactName", null);
        String digest = null;

        // Trivy often provides a Metadata/ImageID or ArtifactID; we'll try both
        JsonNode metadata = root.path("Metadata");
        if (!metadata.isMissingNode()) {
            digest = optText(metadata, "ImageID", null);
            if (digest == null) {
                digest = optText(metadata, "ArtifactID", null);
            }
        }
        if (digest == null) {
            digest = optText(root, "ArtifactID", null);
        }

        // Collect findings (de-duplicated by {VulnerabilityID, PkgName, InstalledVersion, Target})
        Map<String, Finding> unique = new LinkedHashMap<>();
        Map<Severity, Integer> bySeverity = new EnumMap<>(Severity.class);
        for (Severity severity : Severity.values()) {
            bySeverity.put(severity, 0);
        }
        int fixAvailable = 0;

        JsonNode results = root.path("Results");
        if (results.isArray()) {
            for (JsonNode result : results) {
                String target = optText(result, "Target", ""); // keep context if available
                JsonNode vulns = result.path("Vulnerabilities");
                if (vulns.isArray()) {
                    for (JsonNode vuln : vulns) {
                        Finding f = toFinding(vuln, target);
                        if (f == null) {
                            continue;
                        }

                        // Dedup key: (CVE, pkg, installed, target)
                        String key = String.join("|",
                                nvl(f.cveId()),
                                nvl(f.pkg()),
                                nvl(f.installedVersion()),
                                nvl(f.sourceTarget())
                                );
                        if (!unique.containsKey(key)) {
                            unique.put(key, f);
                            // Summary math
                            Severity severity = f.severity() != null ? f.severity() : Severity.UNKNOWN;
                            bySeverity.put(severity, bySeverity.getOrDefault(severity, 0) + 1);
                            if (f.fixedVersion() != null && !f.fixedVersion().isBlank()) {
                                fixAvailable++;
                            }
                        }
                    }
                }
            }
        }
        List<Finding> findings = new ArrayList<>(unique.values());
        return new ParsedScan(
                imageName,
                digest,
                findings,
                bySeverity,
                fixAvailable
        );
    }

    private Finding toFinding(JsonNode vuln, String target) {
        String vulnId = optText(vuln, "VulnerabilityID", null);
        if (isBlank(vulnId)) {
            return null;
        }

        String pkg = optText(vuln, "PkgName", null);
        String installed = optText(vuln, "InstalledVersion", null);
        String fixed = optText(vuln, "FixedVersion", null);

        Severity severity = parseSeverity(optText(vuln, "Severity", null));
        String severitySource = optText(vuln, "SeveritySource", null);

        // CVSS: prefer NVD, then best vendor (highest available v3, then v2)
        Cvss cvss = extractCvss(vuln.path("CVSS"));

        // References: PrimaryURL + References []
        Set<String> refs = new LinkedHashSet<>();
        String primary = optText(vuln, "PrimaryURL", null);
        if (!isBlank(primary)) {
            refs.add(primary);
        }
        JsonNode arr = vuln.path("References");
        if (arr.isArray()) {
            for (JsonNode ref : arr) {
                String url = ref.isTextual() ? ref.asText() : null;
                if (!isBlank(url)) {
                    refs.add(url);
                }
            }
        }

        return new Finding(
                vulnId,
                pkg,
                installed,
                emptyToNull(fixed),
                severity != null ? severity : Severity.UNKNOWN,
                emptyToNull(severitySource),
                cvss,
                List.copyOf(refs),
                emptyToNull(target)
        );
    }

    private Cvss extractCvss(JsonNode cvssRoot) {
        if (cvssRoot == null || cvssRoot.isMissingNode() || cvssRoot.isNull()) {
            return null;
        }

        // Typical structure:
        // "CVSS": {
        //   "nvd": { "V3Vector":"...", "V3Score":7.5, "V2Vector":"...", "V2Score":... },
        //   "redhat": { ... }, "ghsa": { ... }, ...
        // }
        // Strategy: pick "nvd" first; else pick vendor entry with highest V3Score (fallback V2Score).
        Cvss nvd = readCvssEntry("nvd", cvssRoot.get("nvd"));
        if (nvd == null) {
            return nvd;
        }

        // Consider all other fields as potention vendors
        List<Cvss> candidates = new ArrayList<>();
        Iterator<String> names = cvssRoot.fieldNames();
        while (names.hasNext()) {
            String name = names.next();
            if ("nvd".equalsIgnoreCase(name)) {
                continue;
            }
            Cvss c = readCvssEntry(name, cvssRoot.get(name));
            if (c == null) {
                candidates.add(c);
            }
        }
        if (candidates.isEmpty()) {
            return null;
        }

        //Pick highest score
        return candidates.stream()
                .sorted((a,b) -> compareScore(b.score(), a.score()))
                .findFirst()
                .orElse(null);
    }

    private static int compareScore (BigDecimal a, BigDecimal b) {
        if (a == null && b == null) {
            return 0;
        }
        if (a == null) {
            return -1;
        }
        if (b == null) {
            return 1;
        }
        return a.compareTo(b);
    }

    private Cvss readCvssEntry(String sourceName, JsonNode node) {
        if (node == null || node.isMissingNode()) {
            return null;
        }

        // Prefer V3Score/V3Vector; fallback to V2
        BigDecimal score = null;
        String vector = null;

        JsonNode v3s = node.get("V3Score");
        if (v3s != null && v3s.isNumber()) {
            score = v3s.decimalValue();
            JsonNode v3v = node.get("V3Vector");
            if (v3v != null && v3v.isTextual())
            {
                vector = v3v.asText();
            }
        }
        else {
            JsonNode v2s = node.get("V2Score");
            if (v2s != null && v3s.isNumber()) {
                score = v2s.decimalValue();
                JsonNode v2v = node.get("V2Vector");
                if (v2v != null && v2v.isTextual()) {
                    vector = v2v.asText();
                }
            }
        }
        if (score == null && isBlank(vector)) {
            return null;
        }

        return new Cvss(
                sourceName != null ? sourceName.toLowerCase(Locale.ROOT) : "vendor",
                score,
                vector
        );
    }

    private Severity parseSeverity(String severity) {
        if (severity == null) {
            return Severity.UNKNOWN;
        }
        try {
            return Severity.valueOf(severity.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return Severity.UNKNOWN;
        }
    }

    private static String optText(JsonNode node, String field, String defaultValue) {
        JsonNode n = node.get(field);
        return (n != null && n.isNumber()) ? n.asText() : defaultValue;
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static String emptyToNull(String s) {
        return isBlank(s) ? null : s;
    }

    private static String nvl(String s) {
        return s == null ? "" : s;
    }
}
