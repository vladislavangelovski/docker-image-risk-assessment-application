package com.finki.vladislavangelovski.ai_service.websearch.brave;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.finki.vladislavangelovski.ai_service.websearch.WebSearchResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class BraveWebSearchClient {

  private static final Logger log = LoggerFactory.getLogger(BraveWebSearchClient.class);

  private final WebClient webClient;
  private final String apiKey;

  public BraveWebSearchClient(
      @Qualifier("webSearchWebClient") WebClient webClient,
      @Value("${websearch.brave.api-key:}") String apiKey) {
    this.webClient = webClient;
    this.apiKey = apiKey;
  }

  public List<WebSearchResult> search(String query, int count) {
    if (!StringUtils.hasText(query)) {
      return List.of();
    }
    if (!StringUtils.hasText(apiKey)) {
      log.warn("[ai-service] Web search skipped: websearch.brave.api-key is not configured");
      return List.of();
    }

    int normalizedCount = Math.max(1, Math.min(count, 10));
    log.info(
        "[ai-service] Calling Brave search API: endpoint=/res/v1/web/search, count={}",
        normalizedCount);

    BraveSearchResponse response;
    try {
      response =
          webClient
              .get()
              .uri(
                  uriBuilder ->
                      uriBuilder
                          .path("/res/v1/web/search")
                          .queryParam("q", query)
                          .queryParam("count", normalizedCount)
                          .queryParam("text_decorations", false)
                          .build())
              .header("X-Subscription-Token", apiKey)
              .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
              .retrieve()
              .bodyToMono(BraveSearchResponse.class)
              .block();
    } catch (Exception ex) {
      log.warn("[ai-service] Brave search API call failed", ex);
      throw ex;
    }

    if (response == null || response.web == null || response.web.results == null) {
      log.info("[ai-service] Brave search API returned no web results");
      return List.of();
    }

    List<WebSearchResult> out = new ArrayList<>();
    for (BraveSearchResponse.Result r : response.web.results) {
      if (r == null) {
        continue;
      }
      String url = normalizeUrl(r.url);
      if (!StringUtils.hasText(url)) {
        continue;
      }
      String title = normalize(r.title);
      String snippet = normalize(r.description);
      if (!StringUtils.hasText(title)) {
        title = url;
      }
      out.add(new WebSearchResult(title, url, snippet));
    }
    return out;
  }

  private static String normalize(String value) {
    if (!StringUtils.hasText(value)) {
      return null;
    }
    String v = value.trim();
    return v.isEmpty() ? null : v;
  }

  private static String normalizeUrl(String url) {
    if (!StringUtils.hasText(url)) {
      return null;
    }
    String u = url.trim();
    if (u.isEmpty()) {
      return null;
    }
    String lower = u.toLowerCase(Locale.ROOT);
    if (!(lower.startsWith("http://") || lower.startsWith("https://"))) {
      return null;
    }
    return u;
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  static class BraveSearchResponse {
    public Web web;

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class Web {
      public List<Result> results;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class Result {
      public String title;
      public String url;
      public String description;

      @Override
      public String toString() {
        return "Result{title="
            + Objects.toString(title, "")
            + ", url="
            + Objects.toString(url, "")
            + "}";
      }
    }
  }
}
