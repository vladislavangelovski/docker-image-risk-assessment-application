package com.finki.vladislavangelovski.ai_service.service.impl;

import com.finki.vladislavangelovski.ai_service.clients.cve.CveStoreClient;
import com.finki.vladislavangelovski.ai_service.clients.scan.dto.ConfigScanResult;
import com.finki.vladislavangelovski.ai_service.clients.scan.dto.ScanClient;
import com.finki.vladislavangelovski.ai_service.clients.scan.dto.ScanFinding;
import com.finki.vladislavangelovski.ai_service.clients.scan.dto.ScanResult;
import com.finki.vladislavangelovski.ai_service.scoring.RiskScoring;
import com.finki.vladislavangelovski.ai_service.search.VectorSearchService;
import com.finki.vladislavangelovski.ai_service.search.dto.SearchHit;
import com.finki.vladislavangelovski.ai_service.service.AssessmentService;
import com.finki.vladislavangelovski.common.dto.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

@Service
public class AssessmentServiceImpl implements AssessmentService {

  private final ScanClient scanClient;
  private final CveStoreClient cveClient;

  private final int kDefault;
  private final int coverageCap;
  private final double wEpss;
  private final double wCvss;
  private final double coverageBonus;
  private final VectorSearchService vectorSearchService;
  private final int composeMaxParallelImageAssessments;

  public AssessmentServiceImpl(
      ScanClient scanClient,
      CveStoreClient cveClient,
      @Value("${ai.evidence.k-default:6}") int kDefault,
      @Value("${ai.risk.coverage-cap:10}") int coverageCap,
      @Value("${ai.risk.weights.epss:0.65}") double wEpss,
      @Value("${ai.risk.weights.cvss:0.35}") double wCvss,
      @Value("${ai.risk.weights.coverage-bonus:0.15}") double coverageBonus,
      @Value("${ai.compose.max-parallel-image-assessments:1}")
          int composeMaxParallelImageAssessments,
      VectorSearchService vectorSearchService) {
    this.scanClient = scanClient;
    this.cveClient = cveClient;
    this.kDefault = kDefault;
    this.coverageCap = coverageCap;
    this.wEpss = wEpss;
    this.wCvss = wCvss;
    this.coverageBonus = coverageBonus;
    this.composeMaxParallelImageAssessments = Math.max(1, composeMaxParallelImageAssessments);
    this.vectorSearchService = vectorSearchService;
  }

  private static RiskBand band(int score) {
    if (score >= 75) {
      return RiskBand.CRITICAL;
    }
    if (score >= 50) {
      return RiskBand.HIGH;
    }
    if (score >= 25) {
      return RiskBand.MEDIUM;
    }
    return RiskBand.LOW;
  }

  private record ComposeServiceSpec(String imageRef, boolean hasBuild) {}

  private record ImageAssessmentOutcome(AssessImageResponse assessment, String error) {}

  private static String pickBestUrl(CveForEmbedding d) {
    if (d.references() != null && !d.references().isEmpty()) {
      var first = d.references().get(0);
      if (first != null && first.getUrl() != null && !first.getUrl().isBlank()) {
        return first.getUrl();
      }
    }

    return "https://nvd.nist.gov/vuln/detail/" + d.cveId();
  }

  private static String buildQuery(String cveId, List<String> pkgs, String fallbackTitle) {
    String pkgPart = (pkgs == null || pkgs.isEmpty()) ? "" : " " + String.join(", ", pkgs);
    String titlePart =
        (fallbackTitle != null && !fallbackTitle.isBlank()) ? (" " + fallbackTitle) : "";
    return cveId + pkgPart + titlePart;
  }

  private static String truncate(String s, int max) {
    if (s == null) {
      return null;
    }

    return s.length() <= max ? s : s.substring(0, Math.max(0, max - 1)) + "...";
  }

  private static String mostCommonPackagesSummary(List<TopFinding> tfs, int maxPkgs) {
    Map<String, Integer> freq = new LinkedHashMap<>();
    for (TopFinding tf : tfs) {
      if (tf.packages() == null) {
        continue;
      }
      for (String p : tf.packages()) {
        freq.merge(p, 1, Integer::sum);
      }
    }
    return freq.entrySet().stream()
        .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
        .limit(maxPkgs)
        .map(Map.Entry::getKey)
        .collect(Collectors.joining(", "));
  }

  @Override
  public AssessImageResponse assessImage(AssessImageRequest request) {
    int k = request.k() != null ? request.k() : kDefault;

    // 1) Scan
    ScanResult scan = scanClient.scanImage(request.imageRef());
    if (scan == null || scan.findings() == null || scan.findings().isEmpty()) {
      return new AssessImageResponse(
          request.imageRef(),
          0,
          RiskBand.LOW,
          List.of(),
          "No CVEs were found in this image by the scanner.",
          List.of());
    }

    // Flatten counts per CVE and collect package lists
    Map<String, List<String>> packagesByCve = new LinkedHashMap<>();
    for (ScanFinding f : scan.findings()) {
      if (f == null || f.cveId() == null) {
        continue;
      }

      var list = packagesByCve.computeIfAbsent(f.cveId(), id -> new ArrayList<>());
      var pkg = f.packageName();
      if (pkg != null && !pkg.isBlank()) {
        list.add(pkg);
      }
    }

    Map<String, Boolean> fixByCve = new LinkedHashMap<>();
    for (ScanFinding f : scan.findings()) {
      if (f == null || f.cveId() == null) {
        continue;
      }
      boolean hasFix = f.fixedVersion() != null && !f.fixedVersion().isBlank();
      if (hasFix) {
        fixByCve.put(f.cveId(), true);
      } else {
        fixByCve.putIfAbsent(f.cveId(), false);
      }
    }

    List<String> cveIds = new ArrayList<>(packagesByCve.keySet());

    // 2) Fetch CVE details (batch if possible)
    Map<String, Double> cvssByCve = new HashMap<>();
    for (ScanFinding f : scan.findings()) {
      if (f == null || f.cveId() == null || f.cvss() == null || f.cvss().score() == null) {
        continue;
      }
      double score = f.cvss().score();
      cvssByCve.merge(f.cveId(), score, Math::max);
    }
    Map<String, CveForEmbedding> details = cveClient.getByIds(cveIds);

    // 3) Build TopFinding list with risk scores
    List<TopFinding> candidates = new ArrayList<>();
    Map<String, Double> sCveById = new HashMap<>();

    for (String cveId : cveIds) {
      CveForEmbedding d = details.get(cveId);
      if (d == null) {
        continue;
      }

      double epss = d.epss() != null ? d.epss() : 0.0;
      double percentile = d.epssPercentile() != null ? d.epssPercentile() : 0.0;
      double cvss = (d.cvssBase() != null) ? d.cvssBase() : cvssByCve.getOrDefault(cveId, 0.0);
      int instances = packagesByCve.getOrDefault(cveId, List.of()).size();
      double coverageNorm = RiskScoring.coverageNormFromInstances(instances, coverageCap);

      double sCve = RiskScoring.perCveScore(epss, cvss, coverageNorm, wEpss, wCvss, coverageBonus);
      sCveById.put(cveId, sCve);

      String url = pickBestUrl(d);
      String summary =
          (d.description() != null && !d.description().isBlank())
              ? truncate(d.description().replaceAll("\\s+", " "), 160)
              : (d.title() != null && !d.title().isBlank()) ? d.title() : "Vulnerability " + cveId;

      TopFinding tf =
          new TopFinding(
              cveId,
              epss,
              percentile,
              cvss,
              packagesByCve.getOrDefault(cveId, List.of()),
              summary,
              url,
              fixByCve.getOrDefault(cveId, false));
      candidates.add(tf);
    }

    candidates.sort(
        Comparator.comparing((TopFinding t) -> sCveById.getOrDefault(t.cveId(), 0.0))
            .reversed()
            .thenComparing(TopFinding::epss, Comparator.nullsLast(Comparator.reverseOrder())));

    List<TopFinding> topFindings = candidates.stream().limit(k).toList();

    // 4) Overall image score (weighted by epss^2) + band
    Map<String, Double> epssMap =
        topFindings.stream()
            .collect(Collectors.toMap(TopFinding::cveId, TopFinding::epss, (a, b) -> a));
    int overall = RiskScoring.overallImageScore(topFindings, epssMap, wEpss, wCvss);
    RiskBand band = band(overall);

    List<Citation> citations = new ArrayList<>(topFindings.size());
    Set<String> seen = new LinkedHashSet<>();
    for (TopFinding tf : topFindings) {
      if (tf == null || tf.cveId() == null) {
        continue;
      }

      if (seen.add(tf.cveId())) {
        CveForEmbedding d = details.get(tf.cveId());

        String snippet =
            (d != null && d.description() != null && !d.description().isBlank())
                ? truncate(d.description().replaceAll("\\s+", " "), 180)
                : tf.summary();

        citations.add(new Citation(tf.cveId(), tf.url(), snippet));
      }
    }

    String baseExplanation =
        switch (band) {
          case CRITICAL ->
              "High likelihood of exploitation and severe impact across multiple packages. Prioritize "
                  + "immediate"
                  + " patching and rebuild.";
          case HIGH ->
              "Elevated risk: mix of high EPSS and high CVSS findings present. Patch the top issues and "
                  + "redeploy.";
          case MEDIUM ->
              "Moderate risk: review the listed CVEs and plan updates during the next maintenance window.";
          default -> "Low risk based on current EPSS and CVSS signals.";
        };
    String pkgHint = mostCommonPackagesSummary(topFindings, 3);
    String explanation =
        pkgHint.isBlank()
            ? baseExplanation
            : baseExplanation + " Most affected packages: " + pkgHint + ".";
    return new AssessImageResponse(
        request.imageRef(), overall, band, topFindings, explanation, citations);
  }

  @Override
  public AssessComposeResponse assessCompose(AssessComposeRequest request) {
    int k = request.k() != null ? request.k() : kDefault;

    Map<String, ComposeServiceSpec> services = parseComposeServices(request.composeYaml());
    if (services.isEmpty()) {
      ComposeConfigScan configScan = bestEffortComposeConfigScan(request.composeYaml());
      int overall =
          configScan != null && configScan.riskScore() != null ? configScan.riskScore() : 0;
      return new AssessComposeResponse(
          overall,
          band(overall),
          List.of(),
          configScan,
          overall == 0
              ? "No services were found in the docker-compose file."
              : "Risk is based on docker-compose configuration findings only.");
    }

    ComposeConfigScan configScan = bestEffortComposeConfigScan(request.composeYaml());
    int configRisk =
        configScan != null && configScan.riskScore() != null ? configScan.riskScore() : 0;

    List<ComposeServiceAssessment> serviceAssessments = new ArrayList<>();
    int maxImageRisk = 0;
    String maxImageService = null;
    Integer maxImageServiceScore = null;

    Map<String, CompletableFuture<ImageAssessmentOutcome>> byImageRef = new LinkedHashMap<>();
    ExecutorService executor = null;
    List<String> uniqueImages =
        services.values().stream()
            .filter(Objects::nonNull)
            .map(ComposeServiceSpec::imageRef)
            .filter(Objects::nonNull)
            .map(String::trim)
            .filter(s -> !s.isBlank())
            .distinct()
            .toList();

    if (!uniqueImages.isEmpty()) {
      int poolSize = Math.min(composeMaxParallelImageAssessments, uniqueImages.size());
      executor =
          Executors.newFixedThreadPool(
              poolSize,
              r -> {
                Thread t = new Thread(r, "compose-image-assess");
                t.setDaemon(true);
                return t;
              });

      for (String imageRef : uniqueImages) {
        byImageRef.put(
            imageRef,
            CompletableFuture.supplyAsync(
                () -> {
                  try {
                    AssessImageResponse assessment =
                        assessImage(new AssessImageRequest(imageRef, k));
                    return new ImageAssessmentOutcome(assessment, null);
                  } catch (Throwable ex) {
                    String msg = ex.getMessage();
                    if (msg == null || msg.isBlank()) {
                      msg = ex.getClass().getSimpleName();
                    }
                    return new ImageAssessmentOutcome(null, "Failed to assess image: " + msg);
                  }
                },
                executor));
      }
    }

    try {
      for (var entry : services.entrySet()) {
        String serviceName = entry.getKey();
        ComposeServiceSpec spec = entry.getValue();
        String imageRef = spec != null ? spec.imageRef() : null;

        if (imageRef == null || imageRef.isBlank()) {
          String msg =
              spec != null && spec.hasBuild()
                  ? "Service uses build, but no image is set in docker-compose."
                  : "Service has no image configured.";
          serviceAssessments.add(new ComposeServiceAssessment(serviceName, null, null, msg));
          continue;
        }

        String normalizedImageRef = imageRef.trim();

        CompletableFuture<ImageAssessmentOutcome> future = byImageRef.get(normalizedImageRef);
        ImageAssessmentOutcome outcome =
            future != null ? future.join() : new ImageAssessmentOutcome(null, "No scan scheduled");

        serviceAssessments.add(
            new ComposeServiceAssessment(
                serviceName, normalizedImageRef, outcome.assessment(), outcome.error()));

        AssessImageResponse assessment = outcome.assessment();
        if (assessment != null && assessment.overallRisk() != null) {
          int score = assessment.overallRisk();
          if (score > maxImageRisk) {
            maxImageRisk = score;
            maxImageService = serviceName;
            maxImageServiceScore = score;
          }
        }
      }
    } finally {
      if (executor != null) {
        executor.shutdownNow();
      }
    }

    int overall = Math.max(maxImageRisk, configRisk);
    RiskBand band = band(overall);

    StringBuilder explanation = new StringBuilder();
    explanation
        .append("Overall compose risk is ")
        .append(band)
        .append(" (")
        .append(overall)
        .append("/100).");

    if (maxImageService != null && maxImageServiceScore != null) {
      explanation
          .append(" Highest image risk: ")
          .append(maxImageService)
          .append(" (")
          .append(maxImageServiceScore)
          .append("/100).");
    }

    if (configScan != null) {
      if (configScan.error() != null && !configScan.error().isBlank()) {
        explanation.append(" Compose config scan failed.");
      } else if (configScan.totalFindings() != null && configScan.totalFindings() > 0) {
        explanation
            .append(" Compose config findings: ")
            .append(configScan.totalFindings())
            .append(".");
      }
    }

    return new AssessComposeResponse(
        overall, band, serviceAssessments, configScan, explanation.toString().trim());
  }

  private static Map<String, ComposeServiceSpec> parseComposeServices(String composeYaml) {
    LoaderOptions opts = new LoaderOptions();
    opts.setCodePointLimit(1_000_000);
    opts.setMaxAliasesForCollections(50);
    Yaml yaml = new Yaml(new SafeConstructor(opts));

    final Object loaded;
    try {
      loaded = yaml.load(composeYaml);
    } catch (Exception e) {
      throw new IllegalArgumentException("composeYaml must be valid YAML");
    }

    if (!(loaded instanceof Map<?, ?> root)) {
      return Map.of();
    }

    Object servicesObj = root.get("services");
    if (!(servicesObj instanceof Map<?, ?> services)) {
      return Map.of();
    }

    Map<String, ComposeServiceSpec> out = new LinkedHashMap<>();
    for (var entry : services.entrySet()) {
      if (!(entry.getKey() instanceof String serviceName)) {
        continue;
      }
      Object rawSpec = entry.getValue();
      if (!(rawSpec instanceof Map<?, ?> specMap)) {
        out.put(serviceName, new ComposeServiceSpec(null, false));
        continue;
      }

      Object imageObj = specMap.get("image");
      String imageRef = imageObj instanceof String s && !s.isBlank() ? s.trim() : null;

      boolean hasBuild = specMap.get("build") != null;

      out.put(serviceName, new ComposeServiceSpec(imageRef, hasBuild));
    }

    return out;
  }

  private ComposeConfigScan bestEffortComposeConfigScan(String composeYaml) {
    try {
      ConfigScanResult result = scanClient.scanDockerCompose(composeYaml);
      return toComposeConfigScan(result);
    } catch (Exception ex) {
      return new ComposeConfigScan(0, 0, Map.of(), List.of(), null, ex.getMessage());
    }
  }

  private static ComposeConfigScan toComposeConfigScan(ConfigScanResult result) {
    if (result == null) {
      return new ComposeConfigScan(0, 0, Map.of(), List.of(), null, "Empty config scan result");
    }

    Map<String, Integer> severity =
        result.summary() != null ? safeMap(result.summary().severity()) : Map.of();
    int total = result.summary() != null ? result.summary().total() : 0;
    int score = configRiskScore(severity);

    List<ComposeConfigFinding> findings =
        result.findings() == null
            ? List.of()
            : result.findings().stream()
                .filter(Objects::nonNull)
                .limit(50)
                .map(
                    f ->
                        new ComposeConfigFinding(
                            f.id(),
                            f.title(),
                            f.message(),
                            f.severity(),
                            f.primaryUrl(),
                            f.resource(),
                            f.startLine(),
                            f.endLine()))
                .toList();

    return new ComposeConfigScan(score, total, severity, findings, result.scannerVersion(), null);
  }

  private static int configRiskScore(Map<String, Integer> bySeverity) {
    if (bySeverity == null || bySeverity.isEmpty()) {
      return 0;
    }

    int critical = bySeverity.getOrDefault("CRITICAL", 0);
    int high = bySeverity.getOrDefault("HIGH", 0);
    int medium = bySeverity.getOrDefault("MEDIUM", 0);
    int low = bySeverity.getOrDefault("LOW", 0);
    int unknown = bySeverity.getOrDefault("UNKNOWN", 0);

    long raw = 0L;
    raw += (long) critical * 25;
    raw += (long) high * 15;
    raw += (long) medium * 8;
    raw += (long) low * 3;
    raw += (long) unknown;

    return (int) Math.min(100L, raw);
  }

  private static Map<String, Integer> safeMap(Map<String, Integer> in) {
    if (in == null || in.isEmpty()) {
      return Map.of();
    }

    Map<String, Integer> out = new LinkedHashMap<>();
    for (var e : in.entrySet()) {
      if (e.getKey() == null || e.getKey().isBlank() || e.getValue() == null) {
        continue;
      }
      out.put(e.getKey().trim().toUpperCase(Locale.ROOT), Math.max(0, e.getValue()));
    }
    return out;
  }

  private List<Citation> semanticCitationsFor(
      String cveId, List<String> pkgs, String titleOrDesc, int k, double minSim) {
    String q = buildQuery(cveId, pkgs, titleOrDesc);
    List<SearchHit> hits = vectorSearchService.search(q, Math.max(k, 1));
    return hits.stream()
        .filter(h -> h.similarity() == null || h.similarity() >= minSim)
        .limit(k)
        .map(
            h ->
                new Citation(
                    h.cveId(),
                    "https://nvd.nist.gov/vuln/detail/" + h.cveId(),
                    h.title() != null && !h.title().isBlank()
                        ? h.title()
                        : truncate(h.description(), 180) != null
                            ? truncate(h.description(), 180)
                            : ("Relevant evidence for " + h.cveId())))
        .toList();
  }
}
