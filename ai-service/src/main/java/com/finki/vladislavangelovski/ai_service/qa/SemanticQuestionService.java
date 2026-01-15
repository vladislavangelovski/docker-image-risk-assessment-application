package com.finki.vladislavangelovski.ai_service.qa;

import com.finki.vladislavangelovski.ai_service.clients.cve.CveStoreClient;
import com.finki.vladislavangelovski.ai_service.search.VectorSearchService;
import com.finki.vladislavangelovski.ai_service.search.dto.SearchHit;
import com.finki.vladislavangelovski.common.dto.Citation;
import com.finki.vladislavangelovski.common.dto.CveForEmbedding;
import com.finki.vladislavangelovski.common.dto.QaQuestionRequest;
import com.finki.vladislavangelovski.common.dto.QaQuestionResponse;
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
  private static final Pattern CVE_ID_PATTERN = Pattern.compile("CVE-\\d{4}-\\d{4,}");

  private final VectorSearchService vectorSearchService;
  private final ChatClient chatClient;
  private final CveStoreClient cveStoreClient;
  private final PromptTemplates promptTemplates;

  public SemanticQuestionService(
      VectorSearchService vectorSearchService,
      ChatClient chatClient,
      CveStoreClient cveStoreClient,
      PromptTemplates promptTemplates) {
    this.vectorSearchService = vectorSearchService;
    this.chatClient = chatClient;
    this.cveStoreClient = cveStoreClient;
    this.promptTemplates = promptTemplates;
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

  private static String truncate(String text, int maxLen) {
    if (text == null) {
      return "";
    }
    if (text.length() <= maxLen) {
      return text;
    }
    return text.substring(0, maxLen - 3) + "...";
  }

  public QaQuestionResponse answerQuestion(QaQuestionRequest request) {
    return answer(request, null, null);
  }

  public QaQuestionResponse answerQuestionForImage(
      QaQuestionRequest request, Set<String> allowedCves, Map<String, List<String>> packagesByCve) {
    return answer(request, allowedCves, packagesByCve);
  }

  private QaQuestionResponse answer(
      QaQuestionRequest request, Set<String> allowedCves, Map<String, List<String>> packagesByCve) {
    String question = request.question();
    if (question == null || question.isBlank()) {
      throw new IllegalArgumentException("Question must not be null or blank");
    }

    List<SearchHit> hits;
    try {
      hits = vectorSearchService.search(question, RETRIEVAL_K);
    } catch (Exception ex) {
      log.warn("Semantic retrieval failed; falling back to CVE store evidence only", ex);
      hits = List.of();
    }

    if (allowedCves != null && !allowedCves.isEmpty()) {
      hits =
          hits.stream().filter(h -> h.cveId() != null && allowedCves.contains(h.cveId())).toList();
    }

    List<SearchHit> evidence = topN(hits, EVIDENCE_TOP_N);
    if (evidence.isEmpty()) {
      evidence = buildEvidenceFromCveStore(question, allowedCves, EVIDENCE_TOP_N);
    }

    String evidenceText = buildEvidenceText(evidence, packagesByCve);
    List<Citation> citations = buildCitations(evidence);

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
            evidenceText,
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
          citations.stream().map(Citation::cveId).filter(Objects::nonNull).distinct().toList();

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
      return new QaQuestionResponse(fallback, citations, usedCvesFallback, usedPackagesFallback);
    }

    List<String> usedCves =
        citations.stream().map(Citation::cveId).filter(Objects::nonNull).distinct().toList();

    List<String> usedPackages = List.of();
    if (packagesByCve != null && !packagesByCve.isEmpty()) {
      usedPackages =
          usedCves.stream()
              .map(cve -> packagesByCve.getOrDefault(cve, List.of()))
              .flatMap(List::stream)
              .distinct()
              .toList();
    }

    return new QaQuestionResponse(answer, citations, usedCves, usedPackages);
  }
}
