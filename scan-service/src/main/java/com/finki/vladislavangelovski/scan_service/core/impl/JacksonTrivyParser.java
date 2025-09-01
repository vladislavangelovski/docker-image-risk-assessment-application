package com.finki.vladislavangelovski.scan_service.core.impl;

import com.finki.vladislavangelovski.scan_service.core.ParserException;
import com.finki.vladislavangelovski.scan_service.core.TrivyParser;

public class JacksonTrivyParser implements TrivyParser {
    @Override
    public ParsedScan parse(String rawJson) throws ParserException {
        // Minimal shell — we’ll add actual Jackson mapping next.
        // Responsibilities:
        // - Parse Trivy JSON v2 structure
        // - Extract findings, severity counts, fixAvailable, image/digest
        // - Be tolerant to missing fields
        throw new ParserException("Parsing is not implemented yet");
    }
}
