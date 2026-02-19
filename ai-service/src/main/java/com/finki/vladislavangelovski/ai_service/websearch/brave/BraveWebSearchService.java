package com.finki.vladislavangelovski.ai_service.websearch.brave;

import com.finki.vladislavangelovski.ai_service.websearch.WebSearchResult;
import com.finki.vladislavangelovski.ai_service.websearch.WebSearchService;
import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class BraveWebSearchService implements WebSearchService {

  private static final Pattern CVE_ID_PATTERN = Pattern.compile("CVE-\\d{4}-\\d{4,}");
  private static final Pattern VERSION_CHECK_PATTERN =
      Pattern.compile(
          "(?i)(latest|newer|new|current).*(version|release|tag)|check\\s+.*version|is\\s+there\\s+a?\\s*newer");
  private static final Pattern FIX_STATUS_PATTERN =
      Pattern.compile(
          "(?i)((fixed|fix|patched|resolved|mitigated).*(cve|vulnerab))|((cve|vulnerab).*(fixed|patch|resolved|mitigated))");
  private static final Set<String> OFFICIAL_SOURCE_HOSTS =
      Set.of(
          "nvd.nist.gov",
          "cve.mitre.org",
          "mitre.org",
          "hub.docker.com",
          "quay.io",
          "github.com",
          "keycloak.org",
          "www.keycloak.org",
          "postgresql.org",
          "www.postgresql.org",
          "mongodb.com",
          "www.mongodb.com");
  private static final Logger log = LoggerFactory.getLogger(BraveWebSearchService.class);

  private final BraveWebSearchClient client;
  private final boolean enabled;
  private final int maxResults;
  private final String trigger;

  public BraveWebSearchService(
      BraveWebSearchClient client,
      @Value("${websearch.enabled:false}") boolean enabled,
      @Value("${websearch.max-results:5}") int maxResults,
      @Value("${websearch.trigger:cve_only}") String trigger) {
    this.client = client;
    this.enabled = enabled;
    this.maxResults = maxResults;
    this.trigger = trigger;
  }

  @Override
  public List<WebSearchResult> searchFixes(String question, Set<String> allowedCves) {
    if (!enabled) {
      log.info("[ai-service] Web search skipped: websearch.enabled=false");
      return List.of();
    }
    if (!StringUtils.hasText(question)) {
      log.info("[ai-service] Web search skipped: empty question");
      return List.of();
    }

    Set<String> cves = extractCves(question);
    if (allowedCves != null && !allowedCves.isEmpty()) {
      // If user provided an imageRef, allow the scan-derived CVEs to seed the query when
      // configured.
      if ("image_or_cve".equalsIgnoreCase(trigger) && cves.isEmpty()) {
        cves = new LinkedHashSet<>(allowedCves);
      }
    }

    boolean always = "always".equalsIgnoreCase(trigger);
    boolean cveOnly =
        "cve_only".equalsIgnoreCase(trigger) || "image_or_cve".equalsIgnoreCase(trigger);
    if (!always && cveOnly && cves.isEmpty()) {
      log.info("[ai-service] Web search skipped: trigger={} and no CVE IDs detected", trigger);
      return List.of();
    }

    String query = buildQuery(question, cves);
    log.info(
        "[ai-service] Web search triggered: trigger={}, cves={}, maxResults={}",
        trigger,
        cves.size(),
        maxResults);
    List<WebSearchResult> results = client.search(query, maxResults);
    List<WebSearchResult> ranked = prioritizeOfficialSources(results, question, maxResults);
    log.info(
        "[ai-service] Web search completed: {} result(s), sourceHosts={}",
        ranked.size(),
        ranked.stream()
            .map(result -> hostOf(result.url()))
            .filter(StringUtils::hasText)
            .distinct()
            .collect(Collectors.joining(",")));
    return ranked;
  }

  private static String buildQuery(String question, Set<String> cves) {
    String base = question != null ? question.trim() : "";
    boolean versionIntent = isVersionIntent(base);
    boolean fixIntent = isFixIntent(base);

    if (versionIntent && !base.isBlank()) {
      String lower = base.toLowerCase(Locale.ROOT);
      if (lower.contains("keycloak")) {
        return base
            + " site:github.com/keycloak/keycloak/releases site:keycloak.org site:quay.io/keycloak";
      }
      if (lower.contains("postgres")) {
        return base + " site:hub.docker.com/_/postgres site:github.com/docker-library/postgres";
      }
      if (lower.contains("mongo")) {
        return base + " site:hub.docker.com/_/mongo site:github.com/docker-library/mongo";
      }
      return base + " site:github.com site:hub.docker.com site:quay.io";
    }

    if (cves == null || cves.isEmpty()) {
      if (fixIntent) {
        return (base + " site:nvd.nist.gov site:cve.mitre.org remediation").trim();
      }
      return base;
    }

    String joined = String.join(" ", cves.stream().limit(4).toList());
    String officialBias = " site:nvd.nist.gov site:cve.mitre.org site:github.com";
    if (base.isBlank()) {
      return (joined + " remediation fix docker" + officialBias).trim();
    }
    return (base + " " + joined + " remediation fix docker" + officialBias).trim();
  }

  private static List<WebSearchResult> prioritizeOfficialSources(
      List<WebSearchResult> results, String question, int maxResults) {
    if (results == null || results.isEmpty()) {
      return List.of();
    }
    boolean requireOfficial = isVersionIntent(question) || isFixIntent(question);

    List<WebSearchResult> ranked =
        results.stream()
            .filter(r -> r != null && StringUtils.hasText(r.url()))
            .sorted(Comparator.comparingInt(r -> isOfficialSourceUrl(r.url()) ? 0 : 1))
            .limit(Math.max(1, maxResults))
            .toList();

    if (!requireOfficial) {
      return ranked;
    }

    List<WebSearchResult> officialOnly = new ArrayList<>();
    for (WebSearchResult result : ranked) {
      if (isOfficialSourceUrl(result.url())) {
        officialOnly.add(result);
      }
    }
    if (officialOnly.isEmpty()) {
      log.warn("[ai-service] Web search returned no trusted official sources for a claim question");
      return List.of();
    }
    return List.copyOf(officialOnly);
  }

  private static boolean isVersionIntent(String question) {
    if (!StringUtils.hasText(question)) {
      return false;
    }
    return VERSION_CHECK_PATTERN.matcher(question).find();
  }

  private static boolean isFixIntent(String question) {
    if (!StringUtils.hasText(question)) {
      return false;
    }
    return FIX_STATUS_PATTERN.matcher(question).find();
  }

  private static String hostOf(String url) {
    if (!StringUtils.hasText(url)) {
      return null;
    }
    try {
      URI uri = URI.create(url);
      String host = uri.getHost();
      if (!StringUtils.hasText(host)) {
        return null;
      }
      return host.toLowerCase(Locale.ROOT);
    } catch (IllegalArgumentException ex) {
      return null;
    }
  }

  private static boolean isOfficialSourceUrl(String url) {
    String host = hostOf(url);
    if (!StringUtils.hasText(host)) {
      return false;
    }
    String normalized = host.startsWith("www.") ? host.substring(4) : host;
    if (OFFICIAL_SOURCE_HOSTS.contains(normalized)) {
      return true;
    }
    for (String official : OFFICIAL_SOURCE_HOSTS) {
      if (normalized.endsWith("." + official)) {
        return true;
      }
    }
    return false;
  }

  private static Set<String> extractCves(String text) {
    Set<String> out = new LinkedHashSet<>();
    if (!StringUtils.hasText(text)) {
      return out;
    }
    Matcher m = CVE_ID_PATTERN.matcher(text.toUpperCase(Locale.ROOT));
    while (m.find()) {
      out.add(m.group());
    }
    return out;
  }
}
