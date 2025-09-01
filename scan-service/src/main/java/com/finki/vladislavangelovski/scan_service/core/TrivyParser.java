package com.finki.vladislavangelovski.scan_service.core;

import com.finki.vladislavangelovski.scan_service.api.dto.Finding;
import com.finki.vladislavangelovski.scan_service.api.dto.Severity;

import java.util.List;
import java.util.Map;

public interface TrivyParser {
    ParsedScan parse(String rawJson) throws ParserException;

    record ParsedScan(
            String image,
            String digest,
            List<Finding> findings,
            Map<Severity, Integer> bySeverity,
            int fixAvailable
    ) {}
}
