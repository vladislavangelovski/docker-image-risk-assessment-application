package com.finki.vladislavangelovski.ai_service.qa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.finki.vladislavangelovski.ai_service.clients.cve.CveStoreClient;
import com.finki.vladislavangelovski.ai_service.search.VectorSearchService;
import com.finki.vladislavangelovski.common.dto.CveForEmbedding;
import com.finki.vladislavangelovski.common.dto.QaQuestionRequest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.ai.chat.client.ChatClient;

class SemanticQuestionServiceTests {

  @Test
  void returnsEvidenceOnlyResponseWhenChatModelFails() {
    VectorSearchService vectorSearch = mock(VectorSearchService.class);
    when(vectorSearch.search(anyString(), anyInt())).thenReturn(List.of());

    ChatClient chatClient = mock(ChatClient.class, Answers.RETURNS_DEEP_STUBS);
    when(chatClient.prompt().system(anyString()).user(anyString()).call().content())
        .thenThrow(new RuntimeException("HTTP 404 - model not found"));

    CveStoreClient cveStoreClient = mock(CveStoreClient.class);
    when(cveStoreClient.getByIds(List.of("CVE-2021-44228")))
        .thenReturn(
            Map.of(
                "CVE-2021-44228",
                new CveForEmbedding(
                    "CVE-2021-44228", "Log4Shell", "desc", null, 10.0, null, null, null, null,
                    0.9, null)));

    PromptTemplates templates = mock(PromptTemplates.class);
    when(templates.questionSystem()).thenReturn("");
    when(templates.questionUser()).thenReturn("%s\n%s\n%s\n%s");

    SemanticQuestionService service =
        new SemanticQuestionService(vectorSearch, chatClient, cveStoreClient, templates);

    var response =
        service.answerQuestion(new QaQuestionRequest("Is CVE-2021-44228 present?", null, null));

    assertThat(response).isNotNull();
    assertThat(response.answer()).contains("LLM unavailable");
    assertThat(response.citations()).hasSize(1);
    assertThat(response.usedCves()).containsExactly("CVE-2021-44228");
  }
}
