package com.finki.vladislavangelovski.ai_service.clients.scan.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record ScanFinding(
        String cveId,
        List<String> packages,
        @JsonAlias({ "package", "pkg", "pkgName" })
        @JsonProperty("package")
        String packageName,
        String installedVersion,
        String fixedVersion,
        String severity,
        String severitySource,

        Cvss cvss,

        List<String> references,

        @JsonProperty("sourceTarget")
        String sourceTarget
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static record Cvss(
        Double score,
        String vector,
        String source
    ) {}
}
