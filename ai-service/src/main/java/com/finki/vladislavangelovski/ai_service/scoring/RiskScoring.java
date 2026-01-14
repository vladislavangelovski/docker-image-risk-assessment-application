package com.finki.vladislavangelovski.ai_service.scoring;

import com.finki.vladislavangelovski.common.dto.TopFinding;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class RiskScoring {
    private RiskScoring() {
    }
    
    public static double perCveScore(double epss,
                                     double cvssBase,
                                     double coverageNorm,
                                     double wEpss,
                                     double wCvss,
                                     double coverageBonus) {
        double epssTerm = clamp01(epss);
        double cvssTerm = clamp01(cvssBase / 10.0);
        double core = wEpss * epssTerm + wCvss * cvssTerm;
        double boosted = core * (1.0 + coverageBonus * clamp01(coverageNorm));
        return Math.min(100.0, 100.0 * boosted);
    }
    
    public static int overallImageScore(List<TopFinding> top,
                                        Map<String, Double> epssByCve,
                                        double wEpss,
                                        double wCvss) {
        List<TopFinding> list = top.stream().limit(10).toList();
        double sum = 0.0;
        double denom = 0.0;
        for (TopFinding f : list) {
            double epss = clamp01(epssByCve.getOrDefault(f.cveId(), 0.0));
            double w = epss * epss;
            denom += w;
        }
        if (denom == 0.0) {
            return 0;
        }
        for (TopFinding f : list) {
            double epss = clamp01(epssByCve.getOrDefault(f.cveId(), 0.0));
            double w = epss * epss;
            double sCve = 100.0 * (wEpss * epss + wCvss * clamp01(f.cvss() / 10.0));
            sum += (w / denom) * sCve;
        }
        return (int) Math.round(sum);
    }
    
    public static double coverageNormFromInstances(int affectedInstances,
                                                   int cap) {
        int n = Math.max(0, affectedInstances);
        int c = Math.max(1, cap);
        double num = Math.log(1.0 + n);
        double den = Math.log(1.0 + c);
        return clamp01(den == 0.0 ? 0.0 : num / den);
    }
    
    public static Map<String, Double> mapEpssByCve(List<TopFinding> findings) {
        return findings.stream().collect(Collectors.toMap(TopFinding::cveId, TopFinding::epss, (a, b) -> a));
    }
    
    private static double clamp01(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }
}
