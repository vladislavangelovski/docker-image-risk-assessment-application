package com.finki.vladislavangelovski.ai_service.api;

import com.finki.vladislavangelovski.ai_service.embeddings.EmbeddingsClient;
import com.finki.vladislavangelovski.ai_service.indexing.EmbeddingIndexService;
import com.finki.vladislavangelovski.ai_service.vector.SearchHit;
import com.finki.vladislavangelovski.ai_service.vector.VectorStoreRepository;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/embeddings")
public class EmbeddingsAdminController {
  private final EmbeddingIndexService indexService;
  private final EmbeddingsClient embeddings;
  private final VectorStoreRepository vectorRepo;

  public EmbeddingsAdminController(
      EmbeddingIndexService indexService,
      EmbeddingsClient embeddings,
      VectorStoreRepository vectorRepo) {
    this.indexService = indexService;
    this.embeddings = embeddings;
    this.vectorRepo = vectorRepo;
  }

  // --- DTOs ---
  public static class IndexRequest {
    private List<String> cveIds;

    public List<String> getCveIds() {
      return cveIds;
    }

    public void setCveIds(List<String> cveIds) {
      this.cveIds = cveIds;
    }
  }

  public record IndexResponse(int requested, int upserted) {}

  public record SearchResponse(List<SearchHit> items) {}

  /** POST /api/admin/embeddings/index { "cveIds": ["CVE-2024-6119","CVE-2025-27363"] } */
  @PostMapping(
      value = "/index",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public IndexResponse index(@RequestBody(required = false) IndexRequest req) {
    List<String> ids = (req == null || req.getCveIds() == null) ? List.of() : req.getCveIds();

    int upserted;
    if (ids.isEmpty()) {
      // auto-batch mode (dev-safe default)
      upserted = indexService.indexNextBatch(20);
      return new IndexResponse(0, upserted);
    } else {
      upserted = indexService.indexByIds(ids);
      return new IndexResponse(ids.size(), upserted);
    }
  }

  /** GET /api/admin/embeddings/search?q=openssl&k=5 */
  @GetMapping(value = "/search", produces = MediaType.APPLICATION_JSON_VALUE)
  public SearchResponse search(
      @RequestParam("q") String q, @RequestParam(value = "k", defaultValue = "5") int k) {
    var vecs = embeddings.embedAll(List.of(q));
    if (vecs == null || vecs.isEmpty()) {
      return new SearchResponse(List.of());
    }
    var hits = vectorRepo.search(vecs.get(0), Math.max(1, k));
    return new SearchResponse(hits);
  }
}
