package com.finki.vladislavangelovski.ai_service.websearch.brave;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.finki.vladislavangelovski.ai_service.websearch.WebSearchResult;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class BraveWebSearchServiceTests {

  @Test
  void keepsOfficialSourcesForVersionClaims() {
    BraveWebSearchClient client = org.mockito.Mockito.mock(BraveWebSearchClient.class);
    when(client.search(anyString(), anyInt()))
        .thenReturn(
            List.of(
                new WebSearchResult(
                    "Community blog", "https://some-blog.example.com/postgres", "post"),
                new WebSearchResult(
                    "Docker Hub Postgres", "https://hub.docker.com/_/postgres", "official tags")));

    BraveWebSearchService service = new BraveWebSearchService(client, true, 5, "always");

    List<WebSearchResult> results =
        service.searchFixes("what is the latest postgres version?", Set.of());

    assertThat(results).hasSize(1);
    assertThat(results.getFirst().url()).isEqualTo("https://hub.docker.com/_/postgres");

    ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
    verify(client).search(queryCaptor.capture(), org.mockito.ArgumentMatchers.eq(5));
    assertThat(queryCaptor.getValue()).contains("site:hub.docker.com/_/postgres");
  }

  @Test
  void dropsUntrustedResultsForFixClaims() {
    BraveWebSearchClient client = org.mockito.Mockito.mock(BraveWebSearchClient.class);
    when(client.search(anyString(), anyInt()))
        .thenReturn(
            List.of(
                new WebSearchResult(
                    "Forum thread", "https://forum.example.com/cve-2025-6965", "thread")));

    BraveWebSearchService service = new BraveWebSearchService(client, true, 5, "always");

    List<WebSearchResult> results =
        service.searchFixes("is CVE-2025-6965 fixed in keycloak 26.5?", Set.of());

    assertThat(results).isEmpty();
  }
}
