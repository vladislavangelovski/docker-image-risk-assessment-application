package com.finki.vladislavangelovski.ai_service.qa;

import com.finki.vladislavangelovski.ai_service.clients.cve.CveStoreClient;
import com.finki.vladislavangelovski.ai_service.search.VectorSearchService;
import com.finki.vladislavangelovski.ai_service.search.dto.SearchHit;
import com.finki.vladislavangelovski.ai_service.websearch.WebSearchResult;
import com.finki.vladislavangelovski.ai_service.websearch.WebSearchService;
import com.finki.vladislavangelovski.common.dto.Citation;
import com.finki.vladislavangelovski.common.dto.CveForEmbedding;
import com.finki.vladislavangelovski.common.dto.QaChatTurn;
import com.finki.vladislavangelovski.common.dto.QaQuestionRequest;
import com.finki.vladislavangelovski.common.dto.QaQuestionResponse;
import java.net.URI;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class SemanticQuestionService {

  private static final int RETRIEVAL_K = 20;
  private static final int EVIDENCE_TOP_N = 6;
  private static final int DESCRIPTION_MAX_LEN = 600;
  private static final int ASSESSMENT_CONTEXT_MAX_LEN = 8_000;
  private static final int HISTORY_TURNS_MAX = 12;
  private static final int HISTORY_TURN_MAX_LEN = 1_000;
  private static final Pattern CVE_ID_PATTERN = Pattern.compile("CVE-\\d{4}-\\d{4,}");
  private static final Pattern VERSION_CHECK_PATTERN =
      Pattern.compile(
          "(?i)(latest|newer|new|current).*(version|release|tag)|check\\s+.*version|is\\s+there\\s+a?\\s*newer");
  private static final Pattern FIX_STATUS_PATTERN =
      Pattern.compile(
          "(?i)((fixed|fix|patched|resolved|mitigated).*(cve|vulnerab))|((cve|vulnerab).*(fixed|patch|resolved|mitigated))");
  private static final Pattern MARKDOWN_HEADING_PATTERN = Pattern.compile("^\\s{0,3}#{1,6}\\s+");
  private static final Pattern MARKDOWN_BLOCKQUOTE_PATTERN = Pattern.compile("^\\s*>+\\s?");
  private static final Pattern MARKDOWN_BULLET_PATTERN = Pattern.compile("^\\s*[-*+]\\s+");
  private static final Pattern MARKDOWN_NUMBERED_PATTERN = Pattern.compile("^\\s*(\\d+)\\.\\s+");
  private static final Pattern MARKDOWN_LINK_PATTERN =
      Pattern.compile("\\[([^\\]]+)]\\((https?://[^\\s)]+)\\)");
  private static final Pattern MARKDOWN_BOLD_PATTERN = Pattern.compile("(\\*\\*|__)(.+?)\\1");
  private static final Pattern MARKDOWN_INLINE_CODE_PATTERN = Pattern.compile("`([^`]+)`");
  private static final Pattern MULTI_BLANK_LINES_PATTERN = Pattern.compile("\\n{3,}");
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

  private final VectorSearchService vectorSearchService;
  private final ChatClient chatClient;
  private final CveStoreClient cveStoreClient;
  private final PromptTemplates promptTemplates;
  private final WebSearchService webSearchService;

  public SemanticQuestionService(
      VectorSearchService vectorSearchService,
      ChatClient chatClient,
      CveStoreClient cveStoreClient,
      PromptTemplates promptTemplates,
      WebSearchService webSearchService) {
    this.vectorSearchService = vectorSearchService;
    this.chatClient = chatClient;
    this.cveStoreClient = cveStoreClient;
    this.promptTemplates = promptTemplates;
    this.webSearchService = webSearchService;
  }

  private static List<SearchHit> topN(List<SearchHit> hits, int n) {
    if (hits == null || hits.isEmpty()) {
      return List.of();
    }
    int limit = Math.min(n, hits.size());
    return hits.subList(0, limit);
  }

  private static String buildEvidenceText(
      List<SearchHit> hits, Map<String, List<String>> packagesByCve) {
    if (hits == null || hits.isEmpty()) {
      return "No CVE evidence available.";
    }

    StringBuilder sb = new StringBuilder();
    int idx = 1;
    for (SearchHit hit : hits) {
      String cveId = Objects.toString(hit.cveId(), "UNKNOWN");
      String title = Objects.toString(hit.title(), "N/A");
      String desc = truncate(Objects.toString(hit.description(), ""), DESCRIPTION_MAX_LEN);
      Double cvss = hit.cvssBase();
      Double epss = hit.epss();

      sb.append(idx).append(") ").append(cveId);
      if (cvss != null) {
        sb.append(" (CVSS ").append(String.format("%.1f", cvss)).append(")");
      }
      if (epss != null) {
        sb.append(" [EPSS ").append(String.format("%.3f", epss)).append(")");
      }
      sb.append("\n   Title: ").append(title);
      if (!desc.isBlank()) {
        sb.append("\n   Summary: ").append(desc);
      }

      if (packagesByCve != null && cveId != null) {
        List<String> pkgs = packagesByCve.get(cveId);
        if (pkgs != null && !pkgs.isEmpty()) {
          sb.append("\n   Packages in image: ").append(String.join(", ", pkgs));
        }
      }

      sb.append("\n\n");
      idx++;
    }
    return sb.toString().trim();
  }

  private static List<Citation> buildCitations(List<SearchHit> hits) {
    List<Citation> result = new ArrayList<>();
    if (hits == null) {
      return result;
    }
    for (SearchHit hit : hits) {
      if (hit.cveId() == null || hit.cveId().isBlank()) {
        continue;
      }
      String cveId = hit.cveId();
      String url = "https://nvd.nist.gov/vuln/detail/" + cveId;
      String title = hit.title();
      if (title == null || title.isBlank()) {
        title = cveId;
      }
      result.add(new Citation(cveId, url, title));
    }
    return result;
  }

  private static String buildWebContext(List<WebSearchResult> results) {
    if (results == null || results.isEmpty()) {
      return "No web search results.";
    }
    StringBuilder sb = new StringBuilder();
    int idx = 1;
    for (WebSearchResult r : results) {
      if (r == null || r.url() == null || r.url().isBlank()) {
        continue;
      }
      String title = (r.title() == null || r.title().isBlank()) ? r.url() : r.title();
      sb.append(idx).append(") ").append(title).append("\n");
      sb.append("   URL: ").append(r.url()).append("\n");
      if (r.snippet() != null && !r.snippet().isBlank()) {
        sb.append("   Snippet: ").append(truncate(r.snippet(), 400)).append("\n");
      }
      sb.append("\n");
      idx++;
      if (idx > 6) {
        break;
      }
    }
    String out = sb.toString().trim();
    return out.isBlank() ? "No web search results." : out;
  }

  private static List<Citation> buildWebCitations(List<WebSearchResult> results) {
    if (results == null || results.isEmpty()) {
      return List.of();
    }
    List<Citation> out = new ArrayList<>();
    for (WebSearchResult r : results) {
      if (r == null || r.url() == null || r.url().isBlank()) {
        continue;
      }
      String label = r.title();
      if (label == null || label.isBlank()) {
        label = r.snippet();
      }
      if (label == null || label.isBlank()) {
        label = "Web source";
      }
      out.add(new Citation(null, r.url(), truncate(label, 180)));
    }
    return out;
  }

  private static List<Citation> mergeCitations(List<Citation> a, List<Citation> b) {
    if ((a == null || a.isEmpty()) && (b == null || b.isEmpty())) {
      return List.of();
    }
    LinkedHashMap<String, Citation> byUrl = new LinkedHashMap<>();
    if (a != null) {
      for (Citation c : a) {
        if (c != null && c.url() != null && !c.url().isBlank()) {
          byUrl.putIfAbsent(c.url(), c);
        }
      }
    }
    if (b != null) {
      for (Citation c : b) {
        if (c != null && c.url() != null && !c.url().isBlank()) {
          byUrl.putIfAbsent(c.url(), c);
        }
      }
    }
    return List.copyOf(byUrl.values());
  }

  private List<SearchHit> buildEvidenceFromCveStore(
      String text, Set<String> allowedCves, int topN) {
    Set<String> candidates = new LinkedHashSet<>();
    if (allowedCves != null && !allowedCves.isEmpty()) {
      candidates.addAll(allowedCves);
    } else {
      candidates.addAll(extractMentionedCves(text));
    }

    if (candidates.isEmpty()) {
      return List.of();
    }

    List<String> ids = candidates.stream().limit(topN).toList();
    Map<String, CveForEmbedding> byId;
    try {
      byId = cveStoreClient.getByIds(ids);
    } catch (Exception ex) {
      log.debug("CVE store fallback failed for IDs {}", ids, ex);
      return List.of();
    }

    if (byId == null || byId.isEmpty()) {
      return List.of();
    }

    List<SearchHit> hits = new ArrayList<>();
    for (String id : ids) {
      CveForEmbedding cve = byId.get(id);
      if (cve != null) {
        hits.add(toSearchHit(cve));
      }
    }
    return hits;
  }

  private static SearchHit toSearchHit(CveForEmbedding cve) {
    return new SearchHit(
        cve.cveId(), cve.title(), cve.description(), cve.epss(), cve.cvssBase(), null);
  }

  private static Set<String> extractMentionedCves(String text) {
    Set<String> out = new LinkedHashSet<>();
    if (text == null) {
      return out;
    }
    Matcher m = CVE_ID_PATTERN.matcher(text.toUpperCase(Locale.ROOT));
    while (m.find()) {
      out.add(m.group());
    }
    return out;
  }

  private static boolean isVersionCheckQuestion(String question) {
    if (question == null || question.isBlank()) {
      return false;
    }
    return VERSION_CHECK_PATTERN.matcher(question).find();
  }

  private static String truncate(String text, int maxLen) {
    if (text == null) {
      return "";
    }
    if (text.length() <= maxLen) {
      return text;
    }
    return text.substring(0, maxLen - 3) + "...";
  }

  private static String normalizeAssessmentContext(String assessmentContext) {
    if (assessmentContext == null || assessmentContext.isBlank()) {
      return "No assessment context provided.";
    }
    return truncate(assessmentContext.trim(), ASSESSMENT_CONTEXT_MAX_LEN);
  }

  private static String formatChatHistory(List<QaChatTurn> history) {
    if (history == null || history.isEmpty()) {
      return "No prior chat turns.";
    }

    int start = Math.max(0, history.size() - HISTORY_TURNS_MAX);
    StringBuilder sb = new StringBuilder();
    for (int i = start; i < history.size(); i++) {
      QaChatTurn turn = history.get(i);
      if (turn == null || turn.content() == null || turn.content().isBlank()) {
        continue;
      }

      String role = turn.role();
      if (role == null || role.isBlank()) {
        role = "user";
      }
      String normalizedRole = role.trim().toLowerCase(Locale.ROOT);
      if (!normalizedRole.equals("assistant")) {
        normalizedRole = "user";
      }

      String content = truncate(turn.content().trim(), HISTORY_TURN_MAX_LEN);
      sb.append(normalizedRole).append(": ").append(content).append("\n");
    }

    if (sb.isEmpty()) {
      return "No prior chat turns.";
    }

    return sb.toString().trim();
  }

  public QaQuestionResponse answerQuestion(QaQuestionRequest request) {
    return answer(request, null, null, false);
  }

  public QaQuestionResponse answerQuestionToolFirst(QaQuestionRequest request) {
    return answer(request, null, null, true);
  }

  public QaQuestionResponse answerQuestionForImage(
      QaQuestionRequest request, Set<String> allowedCves, Map<String, List<String>> packagesByCve) {
    return answer(request, allowedCves, packagesByCve, false);
  }

  public QaQuestionResponse answerQuestionForImageToolFirst(
      QaQuestionRequest request, Set<String> allowedCves, Map<String, List<String>> packagesByCve) {
    return answer(request, allowedCves, packagesByCve, true);
  }

  private QaQuestionResponse answer(
      QaQuestionRequest request,
      Set<String> allowedCves,
      Map<String, List<String>> packagesByCve,
      boolean forceToolFirstRouting) {
    String question = request.question();
    if (question == null || question.isBlank()) {
      throw new IllegalArgumentException("Question must not be null or blank");
    }

    boolean versionCheckQuestion = isVersionCheckQuestion(question);
    boolean fixStatusQuestion = isFixStatusQuestion(question);
    boolean toolFirstQuestion = forceToolFirstRouting || versionCheckQuestion || fixStatusQuestion;
    if (toolFirstQuestion) {
      log.info("QA tool-first intent detected; prioritizing web/scan evidence");
    }

    List<SearchHit> hits = List.of();
    if (!toolFirstQuestion) {
      try {
        hits = vectorSearchService.search(question, RETRIEVAL_K);
      } catch (Exception ex) {
        log.warn("Semantic retrieval failed; falling back to CVE store evidence only", ex);
        hits = List.of();
      }
    }

    if (allowedCves != null && !allowedCves.isEmpty()) {
      hits =
          hits.stream().filter(h -> h.cveId() != null && allowedCves.contains(h.cveId())).toList();
    }

    List<SearchHit> evidence = topN(hits, EVIDENCE_TOP_N);
    if (!toolFirstQuestion && evidence.isEmpty()) {
      evidence = buildEvidenceFromCveStore(question, allowedCves, EVIDENCE_TOP_N);
    }

    String evidenceText = buildEvidenceText(evidence, packagesByCve);
    List<Citation> citations = buildCitations(evidence);

    List<WebSearchResult> webResults;
    try {
      webResults = webSearchService.searchFixes(question, allowedCves);
    } catch (Exception ex) {
      log.debug("Web search failed; continuing without web evidence", ex);
      webResults = List.of();
    }
    String webContext = buildWebContext(webResults);
    List<Citation> webCitations = buildWebCitations(webResults);
    List<Citation> mergedCitations =
        toolFirstQuestion ? webCitations : mergeCitations(citations, webCitations);
    boolean hasEvidenceForClaim =
        hasClaimEvidence(versionCheckQuestion, fixStatusQuestion, webResults, allowedCves);

    if (toolFirstQuestion && !hasEvidenceForClaim) {
      return buildUnverifiedToolResponse(versionCheckQuestion, fixStatusQuestion);
    }

    String systemPrompt = promptTemplates.questionSystem();

    String allowedCvesStr =
        (allowedCves == null || allowedCves.isEmpty())
            ? "None. No restrictions from image scan; using semantic search only."
            : String.join(", ", allowedCves);

    String packagesByCveStr;
    if (packagesByCve == null || packagesByCve.isEmpty()) {
      packagesByCveStr = "No package info from image scan.";
    } else {
      StringBuilder pkgSb = new StringBuilder();
      packagesByCve.forEach(
          (cve, pkgs) -> {
            pkgSb.append(cve).append(": ").append(String.join(", ", pkgs)).append("\n");
          });
      packagesByCveStr = pkgSb.toString().trim();
    }

    String userPrompt =
        String.format(
            Locale.ROOT,
            promptTemplates.questionUser(),
            question,
            normalizeAssessmentContext(request.assessmentContext()),
            formatChatHistory(request.chatHistory()),
            evidenceText,
            webContext,
            allowedCvesStr,
            packagesByCveStr);

    final String answer;
    try {
      answer = chatClient.prompt().system(systemPrompt).user(userPrompt).call().content();
    } catch (Exception ex) {
      log.warn("Chat model call failed; returning evidence-only answer", ex);
      String msg = ex.getMessage();
      if (msg == null || msg.isBlank()) {
        msg = "LLM call failed";
      }
      List<String> usedCvesFallback =
          mergedCitations.stream()
              .map(Citation::cveId)
              .filter(Objects::nonNull)
              .distinct()
              .toList();

      List<String> usedPackagesFallback = List.of();
      if (packagesByCve != null && !packagesByCve.isEmpty()) {
        usedPackagesFallback =
            usedCvesFallback.stream()
                .map(cve -> packagesByCve.getOrDefault(cve, List.of()))
                .flatMap(List::stream)
                .distinct()
                .toList();
      }
      String fallback =
          "LLM unavailable (" + msg + ").\n\nEvidence used:\n" + truncate(evidenceText, 2000);
      return new QaQuestionResponse(
          normalizeLlmAnswerFormatting(fallback),
          mergedCitations,
          usedCvesFallback,
          usedPackagesFallback);
    }

    List<String> usedCves =
        mergedCitations.stream().map(Citation::cveId).filter(Objects::nonNull).distinct().toList();

    List<String> usedPackages = List.of();
    if (packagesByCve != null && !packagesByCve.isEmpty()) {
      usedPackages =
          usedCves.stream()
              .map(cve -> packagesByCve.getOrDefault(cve, List.of()))
              .flatMap(List::stream)
              .distinct()
              .toList();
    }

    return new QaQuestionResponse(
        normalizeLlmAnswerFormatting(answer), mergedCitations, usedCves, usedPackages);
  }

  private static boolean isFixStatusQuestion(String question) {
    if (question == null || question.isBlank()) {
      return false;
    }
    return FIX_STATUS_PATTERN.matcher(question).find();
  }

  private static boolean hasClaimEvidence(
      boolean versionCheckQuestion,
      boolean fixStatusQuestion,
      List<WebSearchResult> webResults,
      Set<String> allowedCves) {
    boolean hasOfficialWebEvidence =
        webResults != null && webResults.stream().anyMatch(r -> isOfficialSourceUrl(r.url()));
    if (versionCheckQuestion) {
      return hasOfficialWebEvidence;
    }
    if (fixStatusQuestion) {
      return hasOfficialWebEvidence || (allowedCves != null && !allowedCves.isEmpty());
    }
    return webResults != null && !webResults.isEmpty();
  }

  private static QaQuestionResponse buildUnverifiedToolResponse(
      boolean versionCheckQuestion, boolean fixStatusQuestion) {
    if (versionCheckQuestion) {
      return new QaQuestionResponse(
          "I can't verify the latest version yet because I don't have trusted web evidence. "
              + "Please retry once web search is enabled and reachable.",
          List.of(),
          List.of(),
          List.of());
    }
    if (fixStatusQuestion) {
      return new QaQuestionResponse(
          "I can't verify fixed/not-fixed status yet because I don't have trusted web or scan evidence "
              + "for this claim. Provide imageRef and CVE IDs, or retry once web search is available.",
          List.of(),
          List.of(),
          List.of());
    }
    return new QaQuestionResponse(
        "I can't verify this claim yet because evidence is missing.",
        List.of(),
        List.of(),
        List.of());
  }

  private static boolean isOfficialSourceUrl(String url) {
    if (url == null || url.isBlank()) {
      return false;
    }
    try {
      String host = URI.create(url).getHost();
      if (host == null || host.isBlank()) {
        return false;
      }
      String normalized = host.toLowerCase(Locale.ROOT);
      if (normalized.startsWith("www.")) {
        normalized = normalized.substring(4);
      }
      if (OFFICIAL_SOURCE_HOSTS.contains(normalized)) {
        return true;
      }
      for (String official : OFFICIAL_SOURCE_HOSTS) {
        if (normalized.endsWith("." + official)) {
          return true;
        }
      }
      return false;
    } catch (IllegalArgumentException ex) {
      return false;
    }
  }

  private static String normalizeLlmAnswerFormatting(String rawAnswer) {
    if (rawAnswer == null || rawAnswer.isBlank()) {
      return "I could not generate an answer. Please try again.";
    }

    String normalized = rawAnswer.replace("\r\n", "\n").replace('\r', '\n').trim();
    String[] lines = normalized.split("\n");
    List<String> cleanedLines = new ArrayList<>(lines.length);
    boolean previousBlank = false;

    for (String line : lines) {
      String current = line == null ? "" : line.stripTrailing();
      String trimmedLeading = current.stripLeading();
      if (trimmedLeading.startsWith("```")) {
        continue;
      }

      current = MARKDOWN_HEADING_PATTERN.matcher(current).replaceFirst("");
      current = MARKDOWN_BLOCKQUOTE_PATTERN.matcher(current).replaceFirst("");
      current = MARKDOWN_NUMBERED_PATTERN.matcher(current).replaceFirst("$1) ");
      current = MARKDOWN_BULLET_PATTERN.matcher(current).replaceFirst("- ");
      current = MARKDOWN_LINK_PATTERN.matcher(current).replaceAll("$1 ($2)");
      current = MARKDOWN_BOLD_PATTERN.matcher(current).replaceAll("$2");
      current = MARKDOWN_INLINE_CODE_PATTERN.matcher(current).replaceAll("$1");
      current = current.trim();

      if (current.isBlank()) {
        if (!previousBlank) {
          cleanedLines.add("");
          previousBlank = true;
        }
        continue;
      }

      cleanedLines.add(current);
      previousBlank = false;
    }

    String output = String.join("\n", cleanedLines).trim();
    output = MULTI_BLANK_LINES_PATTERN.matcher(output).replaceAll("\n\n").trim();
    if (output.isBlank()) {
      return "I could not generate an answer. Please try again.";
    }
    return output;
  }
}
