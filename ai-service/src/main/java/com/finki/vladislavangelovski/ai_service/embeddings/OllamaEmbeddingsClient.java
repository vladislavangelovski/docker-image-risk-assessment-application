package com.finki.vladislavangelovski.ai_service.embeddings;

import com.finki.vladislavangelovski.ai_service.embeddings.dto.OllamaEmbedResponse;
import com.finki.vladislavangelovski.ai_service.embeddings.dto.OllamaEmbeddingsResponse;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

@Component
public class OllamaEmbeddingsClient implements EmbeddingsClient {
  private static final Logger log = LoggerFactory.getLogger(OllamaEmbeddingsClient.class);
  private final WebClient client;
  private final String model;
  private final int expectedDim;

  public OllamaEmbeddingsClient(
      @Qualifier("embeddingsWebClient") WebClient client,
      @Value("${embeddings.model}") String model,
      @Value("${embeddings.expected-dim}") int expectedDim) {
    this.client = client;
    this.model = model;
    this.expectedDim = expectedDim;
  }

  @Override
  public List<float[]> embedAll(List<String> texts) {
    if (texts == null || texts.isEmpty()) {
      return List.of();
    }

    // Prefer batch endpoint when available: /api/embed { model, input:[...] } -> { embeddings:[[...],...] }
    try {
      var req = Map.of("model", model, "input", texts);
      OllamaEmbedResponse resp =
          client
              .post()
              .uri("/api/embed")
              .contentType(MediaType.APPLICATION_JSON)
              .bodyValue(req)
              .retrieve()
              .bodyToMono(OllamaEmbedResponse.class)
              .block();
      return parseBatch(resp);
    } catch (WebClientResponseException.NotFound ex) {
      // Some Ollama versions expose only /api/embeddings (single prompt). Fall back to per-text calls.
      log.debug("Ollama /api/embed not found; falling back to /api/embeddings per text");
      return embedAllViaEmbeddingsEndpoint(texts);
    } catch (WebClientResponseException ex) {
      log.warn("Ollama embedding request failed (status={}): {}", ex.getStatusCode(), ex.getMessage());
      return List.of();
    } catch (RuntimeException ex) {
      log.warn("Ollama embedding request failed: {}", ex.getMessage());
      return List.of();
    }
  }

  public double[] embedText(String text) {
    if (text == null || text.isBlank()) {
      throw new IllegalArgumentException("text must be non-empty");
    }
    var list = embedAll(Collections.singletonList(text));
    if (list.isEmpty()) {
      throw new IllegalStateException("Ollama returned no embeddings");
    }
    float[] f = list.get(0);
    double[] d = new double[f.length];
    for (int i = 0; i < f.length; i++) {
      d[i] = f[i];
    }
    if (expectedDim > 0 && d.length != expectedDim) {
      throw new IllegalStateException("Embedding dim " + d.length + " != expected " + expectedDim);
    }
    return d;
  }

  private List<float[]> embedAllViaEmbeddingsEndpoint(List<String> texts) {
    List<float[]> out = new ArrayList<>(texts.size());
    for (String text : texts) {
      if (text == null) {
        out.add(new float[0]);
        continue;
      }
      try {
        // /api/embeddings { model, prompt } -> { embedding:[...] }
        var req = Map.of("model", model, "prompt", text);
        OllamaEmbeddingsResponse resp =
            client
                .post()
                .uri("/api/embeddings")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(req)
                .retrieve()
                .bodyToMono(OllamaEmbeddingsResponse.class)
                .block();
        out.add(parseSingle(resp));
      } catch (WebClientResponseException ex) {
        log.warn(
            "Ollama /api/embeddings failed (status={}) for one item; returning empty embeddings",
            ex.getStatusCode());
        return List.of();
      } catch (RuntimeException ex) {
        log.warn("Ollama /api/embeddings failed for one item; returning empty embeddings");
        return List.of();
      }
    }
    return out;
  }

  private List<float[]> parseBatch(OllamaEmbedResponse resp) {
    if (resp == null || resp.embeddings() == null) {
      return List.of();
    }

    List<float[]> out = new ArrayList<>(resp.embeddings().size());
    for (List<Double> row : resp.embeddings()) {
      float[] v = new float[row.size()];
      for (int i = 0; i < row.size(); i++) {
        v[i] = row.get(i).floatValue();
      }
      validateDim(v.length);
      out.add(v);
    }
    return out;
  }

  private float[] parseSingle(OllamaEmbeddingsResponse resp) {
    if (resp == null || resp.embedding() == null) {
      return new float[0];
    }
    List<Double> row = resp.embedding();
    float[] v = new float[row.size()];
    for (int i = 0; i < row.size(); i++) {
      v[i] = row.get(i).floatValue();
    }
    validateDim(v.length);
    return v;
  }

  private void validateDim(int actual) {
    if (expectedDim > 0 && actual != expectedDim) {
      throw new IllegalStateException("Embedding dim " + actual + " != expected " + expectedDim);
    }
  }
}
