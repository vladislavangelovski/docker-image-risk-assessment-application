package com.finki.vladislavangelovski.ai_service.qa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finki.vladislavangelovski.ai_service.clients.cve.CveStoreClient;
import com.finki.vladislavangelovski.ai_service.search.VectorSearchService;
import com.finki.vladislavangelovski.common.dto.CveForEmbedding;
import com.finki.vladislavangelovski.common.dto.QaClaimRequest;
import com.finki.vladislavangelovski.common.dto.Verdict;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.ai.chat.client.ChatClient;

class SemanticClaimServiceTests {

  @Test
  void returnsInsufficientWhenChatModelFails() {
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
    when(templates.claimSystem()).thenReturn("");
    when(templates.claimUser()).thenReturn("%s\n%s\n%s\n%s");

    SemanticClaimService service =
        new SemanticClaimService(
            vectorSearch, chatClient, new ObjectMapper(), cveStoreClient, templates);

    var response =
        service.judgeClaim(
            new QaClaimRequest("CVE-2021-44228 is present", null, null),
            Set.of("CVE-2021-44228"),
            Map.of(),
            null);

    assertThat(response).isNotNull();
    assertThat(response.verdict()).isEqualTo(Verdict.INSUFFICIENT);
    assertThat(response.citations()).isNotEmpty();
  }
}
