package com.finki.vladislavangelovski.ai_service.qa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.finki.vladislavangelovski.ai_service.clients.cve.CveStoreClient;
import com.finki.vladislavangelovski.ai_service.search.VectorSearchService;
import com.finki.vladislavangelovski.ai_service.websearch.WebSearchResult;
import com.finki.vladislavangelovski.ai_service.websearch.WebSearchService;
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
                    "CVE-2021-44228",
                    "Log4Shell",
                    "desc",
                    null,
                    10.0,
                    null,
                    null,
                    null,
                    null,
                    0.9,
                    null)));

    PromptTemplates templates = mock(PromptTemplates.class);
    when(templates.questionSystem()).thenReturn("");
    when(templates.questionUser()).thenReturn("%s\n%s\n%s\n%s");

    WebSearchService webSearchService = mock(WebSearchService.class);

    SemanticQuestionService service =
        new SemanticQuestionService(
            vectorSearch, chatClient, cveStoreClient, templates, webSearchService);

    var response =
        service.answerQuestion(new QaQuestionRequest("Is CVE-2021-44228 present?", null, null));

    assertThat(response).isNotNull();
    assertThat(response.answer()).contains("LLM unavailable");
    assertThat(response.citations()).hasSize(1);
    assertThat(response.usedCves()).containsExactly("CVE-2021-44228");
  }

  @Test
  void returnsUnverifiedResponseForVersionChecksWithoutTrustedWebEvidence() {
    VectorSearchService vectorSearch = mock(VectorSearchService.class);
    ChatClient chatClient = mock(ChatClient.class, Answers.RETURNS_DEEP_STUBS);
    CveStoreClient cveStoreClient = mock(CveStoreClient.class);

    PromptTemplates templates = mock(PromptTemplates.class);
    when(templates.questionSystem()).thenReturn("");
    when(templates.questionUser()).thenReturn("%s\n%s\n%s\n%s");

    WebSearchService webSearchService = mock(WebSearchService.class);
    when(webSearchService.searchFixes(anyString(), any())).thenReturn(List.of());

    SemanticQuestionService service =
        new SemanticQuestionService(
            vectorSearch, chatClient, cveStoreClient, templates, webSearchService);

    var response =
        service.answerQuestionToolFirst(
            new QaQuestionRequest("what is the latest postgres image version?", null, null));

    verify(chatClient, never()).prompt();
    assertThat(response.answer()).contains("can't verify the latest version");
    assertThat(response.citations()).isEmpty();
  }

  @Test
  void keepsVersionAnswersWhenTrustedWebEvidenceExists() {
    VectorSearchService vectorSearch = mock(VectorSearchService.class);

    ChatClient chatClient = mock(ChatClient.class, Answers.RETURNS_DEEP_STUBS);
    when(chatClient.prompt().system(anyString()).user(anyString()).call().content())
        .thenReturn("The latest verified postgres tag is from Docker Hub.");

    CveStoreClient cveStoreClient = mock(CveStoreClient.class);
    PromptTemplates templates = mock(PromptTemplates.class);
    when(templates.questionSystem()).thenReturn("");
    when(templates.questionUser()).thenReturn("%s\n%s\n%s\n%s");

    WebSearchService webSearchService = mock(WebSearchService.class);
    when(webSearchService.searchFixes(anyString(), any()))
        .thenReturn(
            List.of(
                new WebSearchResult(
                    "Official Postgres image tags",
                    "https://hub.docker.com/_/postgres",
                    "official tags")));

    SemanticQuestionService service =
        new SemanticQuestionService(
            vectorSearch, chatClient, cveStoreClient, templates, webSearchService);

    var response =
        service.answerQuestionToolFirst(
            new QaQuestionRequest("what is the latest postgres image version?", null, null));

    assertThat(response.answer()).contains("latest verified postgres");
    assertThat(response.citations()).hasSize(1);
    assertThat(response.citations().getFirst().url())
        .isEqualTo("https://hub.docker.com/_/postgres");
  }

  @Test
  void normalizesMarkdownLikeFormattingFromLlmResponse() {
    VectorSearchService vectorSearch = mock(VectorSearchService.class);
    when(vectorSearch.search(anyString(), anyInt())).thenReturn(List.of());

    ChatClient chatClient = mock(ChatClient.class, Answers.RETURNS_DEEP_STUBS);
    when(chatClient.prompt().system(anyString()).user(anyString()).call().content())
        .thenReturn(
            """
            # Summary
            **Answer:** Patch quickly.

            - Update base image
            - Validate `CVE-2021-44228` status
            See [NVD](https://nvd.nist.gov/vuln/detail/CVE-2021-44228).
            """);

    CveStoreClient cveStoreClient = mock(CveStoreClient.class);
    PromptTemplates templates = mock(PromptTemplates.class);
    when(templates.questionSystem()).thenReturn("");
    when(templates.questionUser()).thenReturn("%s\n%s\n%s\n%s");

    WebSearchService webSearchService = mock(WebSearchService.class);
    when(webSearchService.searchFixes(anyString(), any())).thenReturn(List.of());

    SemanticQuestionService service =
        new SemanticQuestionService(
            vectorSearch, chatClient, cveStoreClient, templates, webSearchService);

    var response = service.answerQuestion(new QaQuestionRequest("How should I remediate this?", null, null));

    assertThat(response.answer()).contains("Summary");
    assertThat(response.answer()).contains("Answer: Patch quickly.");
    assertThat(response.answer()).contains("- Update base image");
    assertThat(response.answer()).contains("- Validate CVE-2021-44228 status");
    assertThat(response.answer())
        .contains("See NVD (https://nvd.nist.gov/vuln/detail/CVE-2021-44228).");
    assertThat(response.answer()).doesNotContain("**");
    assertThat(response.answer()).doesNotContain("[NVD](");
    assertThat(response.answer()).doesNotContain("# ");
    assertThat(response.answer()).doesNotContain("`");
  }
}
