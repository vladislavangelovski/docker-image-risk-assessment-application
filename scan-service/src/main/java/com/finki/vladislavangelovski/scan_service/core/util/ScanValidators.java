package com.finki.vladislavangelovski.scan_service.core.util;

import com.finki.vladislavangelovski.scan_service.api.dto.Finding;
import com.finki.vladislavangelovski.scan_service.api.dto.Severity;
import com.finki.vladislavangelovski.scan_service.api.dto.Summary;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class ScanValidators {
    private ScanValidators() {
    }
    
    public static Summary computeSummary(List<Finding> findings) {
        Map<Severity, Integer> by = new EnumMap<>(Severity.class);
        for (Severity s : Severity.values()) {
            by.put(s, 0);
        }
        int fix = 0;
        for (Finding f : findings) {
            Severity s = f.severity() != null ? f.severity() : Severity.UNKNOWN;
            by.put(s, by.get(s) + 1);
            if (f.fixedVersion() != null && !f.fixedVersion().isBlank()) {
                fix++;
            }
        }
        return new Summary(findings.size(), by, fix);
    }
    
    public static boolean matches(Summary a,
                                  Summary b) {
        if (a == null || b == null) {
            return false;
        }
        if (a.total() != b.total()) {
            return false;
        }
        if (a.fixAvailable() != b.fixAvailable()) {
            return false;
        }
        for (Severity s : Severity.values()) {
            int av = a.severity().getOrDefault(s, 0);
            int bv = b.severity().getOrDefault(s, 0);
            if (av != bv) {
                return false;
            }
        }
        return true;
    }
}
