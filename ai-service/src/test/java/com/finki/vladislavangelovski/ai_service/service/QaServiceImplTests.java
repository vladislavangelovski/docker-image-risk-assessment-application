package com.finki.vladislavangelovski.ai_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.finki.vladislavangelovski.ai_service.clients.scan.dto.ScanClient;
import com.finki.vladislavangelovski.ai_service.clients.scan.dto.ScanFinding;
import com.finki.vladislavangelovski.ai_service.clients.scan.dto.ScanResult;
import com.finki.vladislavangelovski.ai_service.history.QaConversationHistoryService;
import com.finki.vladislavangelovski.ai_service.history.QaUserContext;
import com.finki.vladislavangelovski.ai_service.indexing.EmbeddingIndexService;
import com.finki.vladislavangelovski.ai_service.qa.SemanticQuestionService;
import com.finki.vladislavangelovski.ai_service.service.impl.QaServiceImpl;
import com.finki.vladislavangelovski.common.dto.QaQuestionRequest;
import java.util.List;
import org.junit.jupiter.api.Test;

class QaServiceImplTests {

  @Test
  void verifiesFixesByScanningTargetTagWhenRequested() {
    SemanticQuestionService semantic = mock(SemanticQuestionService.class);
    ScanClient scanClient = mock(ScanClient.class);
    EmbeddingIndexService indexService = mock(EmbeddingIndexService.class);
    QaConversationHistoryService history = mock(QaConversationHistoryService.class);
    when(history.recordQuestion(any(), any(), any())).thenReturn(null);

    when(scanClient.scanImage("quay.io/keycloak/keycloak:26.5"))
        .thenReturn(
            new ScanResult(
                "quay.io/keycloak/keycloak:26.5",
                List.of(
                    new ScanFinding(
                        "CVE-2025-6965",
                        List.of("openssl"),
                        "openssl",
                        "3.0.7",
                        "3.0.8",
                        "HIGH",
                        "nvd",
                        null,
                        List.of(),
                        "quay.io/keycloak/keycloak:26.5"))));

    QaService service = new QaServiceImpl(semantic, scanClient, indexService, history);
    QaQuestionRequest request =
        new QaQuestionRequest(
            "are CVEs fixed in keycloak 26.5?",
            null,
            6,
            """
            1. Service keycloak | Image quay.io/keycloak/keycloak:26.0.0
               - CVE-2025-6965
               - CVE-2025-50059
            """,
            List.of(),
            "assessment|default",
            null);

    var response = service.answerQuestion(request, new QaUserContext("u-1", "demo"));

    verify(scanClient).scanImage("quay.io/keycloak/keycloak:26.5");
    verify(semantic, never()).answerQuestion(any());

    assertThat(response.answer()).contains("Still present (1): CVE-2025-6965");
    assertThat(response.answer()).contains("Not detected (1): CVE-2025-50059");
    assertThat(response.usedCves()).containsExactly("CVE-2025-6965", "CVE-2025-50059");
    assertThat(response.citations()).hasSize(2);
  }

  @Test
  void fallsBackToSemanticWhenQuestionIsNotFixCheck() {
    SemanticQuestionService semantic = mock(SemanticQuestionService.class);
    ScanClient scanClient = mock(ScanClient.class);
    EmbeddingIndexService indexService = mock(EmbeddingIndexService.class);
    QaConversationHistoryService history = mock(QaConversationHistoryService.class);
    when(history.recordQuestion(any(), any(), any())).thenReturn(null);
    when(semantic.answerQuestion(any()))
        .thenReturn(
            new com.finki.vladislavangelovski.common.dto.QaQuestionResponse(
                "ok", List.of(), List.of(), List.of()));

    QaService service = new QaServiceImpl(semantic, scanClient, indexService, history);
    QaQuestionRequest request = new QaQuestionRequest("what is keycloak?", null, 6);

    var response = service.answerQuestion(request, new QaUserContext("u-1", "demo"));

    verify(semantic).answerQuestion(any());
    verify(scanClient, never()).scanImage(anyString());
    assertThat(response.answer()).isEqualTo("ok");
  }

  @Test
  void routesVersionQuestionsThroughToolFirstSemanticPath() {
    SemanticQuestionService semantic = mock(SemanticQuestionService.class);
    ScanClient scanClient = mock(ScanClient.class);
    EmbeddingIndexService indexService = mock(EmbeddingIndexService.class);
    QaConversationHistoryService history = mock(QaConversationHistoryService.class);
    when(history.recordQuestion(any(), any(), any())).thenReturn(null);
    when(semantic.answerQuestionToolFirst(any()))
        .thenReturn(
            new com.finki.vladislavangelovski.common.dto.QaQuestionResponse(
                "I can't verify the latest version yet because I don't have trusted web evidence.",
                List.of(),
                List.of(),
                List.of()));

    QaService service = new QaServiceImpl(semantic, scanClient, indexService, history);
    QaQuestionRequest request =
        new QaQuestionRequest("what is the latest postgres version?", null, 6);

    var response = service.answerQuestion(request, new QaUserContext("u-1", "demo"));

    verify(semantic).answerQuestionToolFirst(any());
    verify(semantic, never()).answerQuestion(any());
    verify(scanClient, never()).scanImage(anyString());
    assertThat(response.answer()).contains("can't verify the latest version");
  }
}
