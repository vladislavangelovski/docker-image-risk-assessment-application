package com.finki.vladislavangelovski.ai_service.service.impl;

import com.finki.vladislavangelovski.ai_service.clients.scan.dto.ScanClient;
import com.finki.vladislavangelovski.ai_service.clients.scan.dto.ScanFinding;
import com.finki.vladislavangelovski.ai_service.clients.scan.dto.ScanResult;
import com.finki.vladislavangelovski.ai_service.history.QaConversationHistoryService;
import com.finki.vladislavangelovski.ai_service.history.QaUserContext;
import com.finki.vladislavangelovski.ai_service.indexing.EmbeddingIndexService;
import com.finki.vladislavangelovski.ai_service.qa.SemanticQuestionService;
import com.finki.vladislavangelovski.ai_service.service.QaService;
import com.finki.vladislavangelovski.common.dto.QaQuestionRequest;
import com.finki.vladislavangelovski.common.dto.QaQuestionResponse;
import java.util.*;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class QaServiceImpl implements QaService {

  private static final Logger log = LoggerFactory.getLogger(QaServiceImpl.class);

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
    String imageRef = request.imageRef();
    boolean hasImage = imageRef != null && !imageRef.isBlank();

    QaQuestionResponse semanticResponse;

    if (!hasImage) {
      semanticResponse = semanticQuestionService.answerQuestion(request);
      return withConversationPersistence(userContext, request, semanticResponse);
    }

    ScanResult scan;

    try {
      scan = scanClient.scanImage(imageRef);
    } catch (Exception e) {
      semanticResponse = semanticQuestionService.answerQuestion(request);
      return withConversationPersistence(userContext, request, semanticResponse);
    }

    if (scan == null || scan.findings() == null || scan.findings().isEmpty()) {
      semanticResponse = semanticQuestionService.answerQuestion(request);
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
      semanticResponse = semanticQuestionService.answerQuestion(request);
      return withConversationPersistence(userContext, request, semanticResponse);
    }

    autoIndexCves(allowedCves);

    semanticResponse =
        semanticQuestionService.answerQuestionForImage(
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
