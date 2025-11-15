package com.finki.vladislavangelovski.ai_service.api;

import com.finki.vladislavangelovski.ai_service.embeddings.EmbeddingsClient;
import com.finki.vladislavangelovski.ai_service.indexing.EmbeddingIndexService;
import com.finki.vladislavangelovski.ai_service.vector.SearchHit;
import com.finki.vladislavangelovski.ai_service.vector.VectorStoreRepository;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/embeddings")
public class EmbeddingsAdminController {
    private final EmbeddingIndexService indexService;
    private final EmbeddingsClient embeddings;
    private final VectorStoreRepository vectorRepo;
    
    public EmbeddingsAdminController(EmbeddingIndexService indexService,
                                     EmbeddingsClient embeddings,
                                     VectorStoreRepository vectorRepo) {
        this.indexService = indexService;
        this.embeddings = embeddings;
        this.vectorRepo = vectorRepo;
    }
    
    // --- DTOs ---
    public record IndexRequest(List<String> cveIds) {
    }
    
    public record IndexResponse(
            int requested,
            int upserted
    ) {
    }
    
    public record SearchResponse(List<SearchHit> items) {
    }
    
    /**
     * POST /api/admin/embeddings/index  { "cveIds": ["CVE-2024-6119","CVE-2025-27363"] }
     */
    @PostMapping(value = "/index", consumes = MediaType.APPLICATION_JSON_VALUE, produces =
            MediaType.APPLICATION_JSON_VALUE)
    public IndexResponse index(@RequestBody IndexRequest req) {
        List<String> ids = (req == null || req.cveIds() == null) ? List.of() : req.cveIds();
        int upserted = indexService.indexByIds(ids);
        return new IndexResponse(ids.size(), upserted);
    }
    
    /**
     * GET /api/admin/embeddings/search?q=openssl&k=5
     */
    @GetMapping(value = "/search", produces = MediaType.APPLICATION_JSON_VALUE)
    public SearchResponse search(@RequestParam("q") String q,
                                 @RequestParam(value = "k", defaultValue = "5") int k) {
        // embed the query text as a single-item batch
        var vecs = embeddings.embedAll(List.of(q));
        if (vecs == null || vecs.isEmpty()) {
            return new SearchResponse(List.of());
        }
        var hits = vectorRepo.search(vecs.get(0), Math.max(1, k));
        return new SearchResponse(hits);
    }
}
