package com.finki.vladislavangelovski.ai_service.api;

import com.finki.vladislavangelovski.ai_service.search.VectorSearchService;
import com.finki.vladislavangelovski.ai_service.search.dto.SearchHit;
import com.finki.vladislavangelovski.ai_service.search.dto.SearchRequest;
import com.finki.vladislavangelovski.ai_service.search.dto.SearchResponse;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/semantic")
public class SemanticSearchController {

  private final VectorSearchService svc;

  public SemanticSearchController(VectorSearchService svc) {
    this.svc = svc;
  }

  @PostMapping(
      value = "/search",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public SearchResponse search(@RequestBody(required = false) SearchRequest req) {
    if (req == null) {
      throw new IllegalArgumentException("request body is required");
    }
    int k = (req.k() == null) ? 10 : Math.max(1, Math.min(50, req.k()));
    long t0 = System.currentTimeMillis();
    List<SearchHit> hits = svc.search(req.query(), k);
    return new SearchResponse((int) (System.currentTimeMillis() - t0), k, hits);
  }

  // Handy GET for quick testing: /api/semantic/search?q=openssl RCE&k=5
  @GetMapping(value = "/search", produces = MediaType.APPLICATION_JSON_VALUE)
  public SearchResponse searchGet(
      @RequestParam("q") String q, @RequestParam(name = "k", defaultValue = "10") int k) {
    k = Math.max(1, Math.min(50, k));
    long t0 = System.currentTimeMillis();
    List<SearchHit> hits = svc.search(q, k);
    return new SearchResponse((int) (System.currentTimeMillis() - t0), k, hits);
  }
}
