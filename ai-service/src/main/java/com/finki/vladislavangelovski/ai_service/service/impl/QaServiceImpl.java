package com.finki.vladislavangelovski.ai_service.service.impl;

import com.finki.vladislavangelovski.ai_service.clients.scan.dto.ScanClient;
import com.finki.vladislavangelovski.ai_service.clients.scan.dto.ScanFinding;
import com.finki.vladislavangelovski.ai_service.clients.scan.dto.ScanResult;
import com.finki.vladislavangelovski.ai_service.history.QaConversationHistoryService;
import com.finki.vladislavangelovski.ai_service.history.QaUserContext;
import com.finki.vladislavangelovski.ai_service.indexing.EmbeddingIndexService;
import com.finki.vladislavangelovski.ai_service.qa.SemanticQuestionService;
import com.finki.vladislavangelovski.ai_service.service.QaService;
import com.finki.vladislavangelovski.common.dto.Citation;
import com.finki.vladislavangelovski.common.dto.QaQuestionRequest;
import com.finki.vladislavangelovski.common.dto.QaQuestionResponse;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class QaServiceImpl implements QaService {

  private static final Logger log = LoggerFactory.getLogger(QaServiceImpl.class);
  private static final Pattern CVE_ID_PATTERN = Pattern.compile("CVE-\\d{4}-\\d{4,}");
  private static final Pattern IMAGE_REF_PATTERN =
      Pattern.compile("([a-z0-9._/-]+:[A-Za-z0-9._-]+)");
  private static final Pattern PRODUCT_VERSION_PATTERN =
      Pattern.compile(
          "\\b([a-z][a-z0-9_-]{2,})\\s+(\\d+\\.\\d+(?:\\.\\d+)?)\\b", Pattern.CASE_INSENSITIVE);
  private static final Pattern VERSION_CHECK_PATTERN =
      Pattern.compile(
          "(?i)(latest|newer|new|current).*(version|release|tag)|check\\s+.*version|is\\s+there\\s+a?\\s*newer");

  private final SemanticQuestionService semanticQuestionService;
  private final ScanClient scanClient;
  private final EmbeddingIndexService embeddingIndexService;
  private final QaConversationHistoryService conversationHistoryService;

  public QaServiceImpl(
      SemanticQuestionService semanticQuestionService,
      ScanClient scanClient,
      EmbeddingIndexService embeddingIndexService,
      QaConversationHistoryService conversationHistoryService) {
    this.semanticQuestionService = semanticQuestionService;
    this.scanClient = scanClient;
    this.embeddingIndexService = embeddingIndexService;
    this.conversationHistoryService = conversationHistoryService;
  }

  @Override
  public QaQuestionResponse answerQuestion(QaQuestionRequest request, QaUserContext userContext) {
    String question = request != null ? request.question() : null;
    boolean fixVerificationQuestion = isFixVerificationQuestion(question);
    boolean versionCheckQuestion = isVersionCheckQuestion(question);
    boolean toolFirstQuestion = fixVerificationQuestion || versionCheckQuestion;

    QaQuestionResponse fixedCheckResponse = buildFixVerificationResponse(request);
    if (fixedCheckResponse != null) {
      return withConversationPersistence(userContext, request, fixedCheckResponse);
    }

    String imageRef = request.imageRef();
    boolean hasImage = StringUtils.hasText(imageRef);

    QaQuestionResponse semanticResponse;

    if (!hasImage) {
      semanticResponse =
          toolFirstQuestion
              ? semanticQuestionService.answerQuestionToolFirst(request)
              : semanticQuestionService.answerQuestion(request);
      return withConversationPersistence(userContext, request, semanticResponse);
    }

    ScanResult scan;

    try {
      scan = scanClient.scanImage(imageRef);
    } catch (Exception e) {
      semanticResponse =
          toolFirstQuestion
              ? semanticQuestionService.answerQuestionToolFirst(request)
              : semanticQuestionService.answerQuestion(request);
      return withConversationPersistence(userContext, request, semanticResponse);
    }

    if (scan == null || scan.findings() == null || scan.findings().isEmpty()) {
      semanticResponse =
          toolFirstQuestion
              ? semanticQuestionService.answerQuestionToolFirst(request)
              : semanticQuestionService.answerQuestion(request);
      return withConversationPersistence(userContext, request, semanticResponse);
    }

    Map<String, List<String>> packagesByCve = new LinkedHashMap<>();
    for (ScanFinding f : scan.findings()) {
      if (f == null || f.cveId() == null || f.cveId().isBlank()) continue;

      List<String> pkgs = new ArrayList<>();
      if (f.packages() != null) {
        pkgs.addAll(f.packages());
      }
      if (f.packageName() != null && !f.packageName().isBlank()) {
        pkgs.add(f.packageName());
      }
      packagesByCve.computeIfAbsent(f.cveId(), id -> new ArrayList<>()).addAll(pkgs);
    }

    Set<String> allowedCves = packagesByCve.keySet();

    if (allowedCves.isEmpty()) {
      semanticResponse =
          toolFirstQuestion
              ? semanticQuestionService.answerQuestionToolFirst(request)
              : semanticQuestionService.answerQuestion(request);
      return withConversationPersistence(userContext, request, semanticResponse);
    }

    autoIndexCves(allowedCves);

    semanticResponse =
        toolFirstQuestion
            ? semanticQuestionService.answerQuestionForImageToolFirst(
                request,
                allowedCves,
                packagesByCve.entrySet().stream()
                    .collect(
                        Collectors.toMap(
                            Map.Entry::getKey,
                            e -> List.copyOf(e.getValue()),
                            (a, b) -> a,
                            LinkedHashMap::new)))
            : semanticQuestionService.answerQuestionForImage(
                request,
                allowedCves,
                packagesByCve.entrySet().stream()
                    .collect(
                        Collectors.toMap(
                            Map.Entry::getKey,
                            e -> List.copyOf(e.getValue()),
                            (a, b) -> a,
                            LinkedHashMap::new)));
    return withConversationPersistence(userContext, request, semanticResponse);
  }

  private QaQuestionResponse buildFixVerificationResponse(QaQuestionRequest request) {
    String question = request != null ? request.question() : null;
    if (!isFixVerificationQuestion(question)) {
      return null;
    }

    Set<String> cves = extractCves(question);
    if (cves.isEmpty()) {
      cves = extractCves(request.assessmentContext());
    }
    if (cves.isEmpty()) {
      return null;
    }

    String targetImage = resolveTargetImage(request);
    if (!StringUtils.hasText(targetImage)) {
      String answer =
          "I can't verify whether those CVEs are fixed because I don't have a target image tag to scan. "
              + "Please provide `imageRef` (for example: `quay.io/keycloak/keycloak:26.5`).";
      return new QaQuestionResponse(answer, List.of(), List.copyOf(cves), List.of());
    }

    final ScanResult scan;
    try {
      scan = scanClient.scanImage(targetImage);
    } catch (Exception ex) {
      String msg = ex.getMessage();
      if (!StringUtils.hasText(msg)) {
        msg = ex.getClass().getSimpleName();
      }
      String answer =
          "I tried to verify fixes by scanning `" + targetImage + "`, but the scan failed: " + msg;
      return new QaQuestionResponse(answer, List.of(), List.copyOf(cves), List.of());
    }

    Set<String> present = new LinkedHashSet<>();
    Map<String, List<String>> packagesByCve = new LinkedHashMap<>();
    if (scan != null && scan.findings() != null) {
      for (ScanFinding finding : scan.findings()) {
        if (finding == null || !StringUtils.hasText(finding.cveId())) {
          continue;
        }
        String cve = finding.cveId().trim().toUpperCase(Locale.ROOT);
        present.add(cve);
        List<String> packages = new ArrayList<>();
        if (finding.packages() != null) {
          packages.addAll(finding.packages());
        }
        if (StringUtils.hasText(finding.packageName())) {
          packages.add(finding.packageName().trim());
        }
        if (!packages.isEmpty()) {
          packagesByCve.computeIfAbsent(cve, ignored -> new ArrayList<>()).addAll(packages);
        }
      }
    }

    List<String> stillPresent = cves.stream().filter(present::contains).sorted().toList();
    List<String> notDetected =
        cves.stream().filter(cve -> !present.contains(cve)).sorted().toList();

    List<Citation> citations = new ArrayList<>();
    for (String cve : cves) {
      String status =
          present.contains(cve)
              ? "Detected in scanned image " + targetImage
              : "Not detected in scanned image " + targetImage;
      citations.add(new Citation(cve, "https://nvd.nist.gov/vuln/detail/" + cve, status));
    }

    List<String> usedPackages =
        stillPresent.stream()
            .map(cve -> packagesByCve.getOrDefault(cve, List.of()))
            .flatMap(List::stream)
            .filter(StringUtils::hasText)
            .distinct()
            .toList();

    StringBuilder answer = new StringBuilder();
    answer
        .append("I checked `")
        .append(targetImage)
        .append("` against ")
        .append(cves.size())
        .append(" CVE(s) from your context.\n\n");
    answer.append("Still present (").append(stillPresent.size()).append("): ");
    answer.append(stillPresent.isEmpty() ? "none" : String.join(", ", stillPresent));
    answer.append("\n");
    answer.append("Not detected (").append(notDetected.size()).append("): ");
    answer.append(notDetected.isEmpty() ? "none" : String.join(", ", notDetected));
    answer.append("\n\n");
    answer.append(
        "`Not detected` means the current scanner output for this image tag did not report that CVE.");

    return new QaQuestionResponse(answer.toString(), citations, List.copyOf(cves), usedPackages);
  }

  private static boolean isFixVerificationQuestion(String question) {
    if (!StringUtils.hasText(question)) {
      return false;
    }
    String q = question.toLowerCase(Locale.ROOT);
    boolean asksFixStatus =
        q.contains("fixed")
            || q.contains("fix")
            || q.contains("patched")
            || q.contains("resolved")
            || q.contains("mitigated");
    boolean referencesVulns = q.contains("cve") || q.contains("vulnerab");
    return asksFixStatus && referencesVulns;
  }

  private static boolean isVersionCheckQuestion(String question) {
    if (!StringUtils.hasText(question)) {
      return false;
    }
    return VERSION_CHECK_PATTERN.matcher(question).find();
  }

  private static Set<String> extractCves(String text) {
    Set<String> out = new LinkedHashSet<>();
    if (!StringUtils.hasText(text)) {
      return out;
    }
    Matcher matcher = CVE_ID_PATTERN.matcher(text.toUpperCase(Locale.ROOT));
    while (matcher.find()) {
      out.add(matcher.group());
    }
    return out;
  }

  private static List<String> extractImageRefs(String text) {
    if (!StringUtils.hasText(text)) {
      return List.of();
    }
    LinkedHashSet<String> out = new LinkedHashSet<>();
    Matcher matcher = IMAGE_REF_PATTERN.matcher(text);
    while (matcher.find()) {
      String imageRef = matcher.group(1);
      if (StringUtils.hasText(imageRef)) {
        out.add(imageRef.trim());
      }
    }
    return List.copyOf(out);
  }

  private static String resolveTargetImage(QaQuestionRequest request) {
    if (request == null) {
      return null;
    }
    if (StringUtils.hasText(request.imageRef())) {
      return request.imageRef().trim();
    }

    List<String> directFromQuestion = extractImageRefs(request.question());
    if (!directFromQuestion.isEmpty()) {
      return directFromQuestion.getFirst();
    }

    List<String> contextImages = extractImageRefs(request.assessmentContext());
    if (contextImages.isEmpty()) {
      return null;
    }

    String question = request.question() != null ? request.question() : "";
    Matcher matcher = PRODUCT_VERSION_PATTERN.matcher(question);
    while (matcher.find()) {
      String product = matcher.group(1).toLowerCase(Locale.ROOT);
      String version = matcher.group(2);
      for (String baseImage : contextImages) {
        String lower = baseImage.toLowerCase(Locale.ROOT);
        if (lower.contains("/" + product + "/")
            || lower.contains("/" + product + ":")
            || lower.contains(product + ":")) {
          return replaceTag(baseImage, version);
        }
      }
    }

    return null;
  }

  private static String replaceTag(String imageRef, String newTag) {
    if (!StringUtils.hasText(imageRef) || !StringUtils.hasText(newTag)) {
      return imageRef;
    }
    int lastColon = imageRef.lastIndexOf(':');
    int lastSlash = imageRef.lastIndexOf('/');
    if (lastColon > lastSlash) {
      return imageRef.substring(0, lastColon + 1) + newTag;
    }
    return imageRef + ":" + newTag;
  }

  private void autoIndexCves(Set<String> cveIds) {
    try {
      int upserted = embeddingIndexService.indexMissingByIds(cveIds);
      if (upserted > 0) {
        log.info("Auto-indexed {} CVE embeddings for QA flow", upserted);
      }
    } catch (Exception ex) {
      log.warn("Auto-indexing CVE embeddings failed; continuing without it", ex);
    }
  }

  private QaQuestionResponse withConversationPersistence(
      QaUserContext userContext, QaQuestionRequest request, QaQuestionResponse semanticResponse) {
    String conversationId =
        conversationHistoryService.recordQuestion(userContext, request, semanticResponse);
    if (!StringUtils.hasText(conversationId)) {
      return semanticResponse;
    }
    return new QaQuestionResponse(
        semanticResponse.answer(),
        semanticResponse.citations(),
        semanticResponse.usedCves(),
        semanticResponse.usedPackages(),
        conversationId);
  }
}
