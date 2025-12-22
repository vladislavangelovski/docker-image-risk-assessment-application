package com.finki.vladislavangelovski.ai_service.indexing;

import com.finki.vladislavangelovski.ai_service.clients.cve.CveStoreClient;
import com.finki.vladislavangelovski.ai_service.embeddings.EmbeddingsClient;
import com.finki.vladislavangelovski.ai_service.vector.VectorStoreRepository;
import com.finki.vladislavangelovski.common.dto.CveForEmbedding;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class EmbeddingIndexService {
    private final CveStoreClient cveClient;
    private final EmbeddingsClient embeddings;
    private final VectorStoreRepository vectorRepo;
    
    /**
     * Dev-friendly cursor used only by the “index next batch” endpoint to move
     * past page 0 without the caller having to supply a page param.
     *
     * NOTE: This is intentionally in-memory (single instance in docker-compose).
     */
    private final AtomicInteger autoPageCursor = new AtomicInteger(0);
    
    public EmbeddingIndexService(CveStoreClient cveClient,
                                 EmbeddingsClient embeddings,
                                 VectorStoreRepository vectorRepo) {
        this.cveClient = cveClient;
        this.embeddings = embeddings;
        this.vectorRepo = vectorRepo;
    }
    
    /**
     * Fetch CVEs, embed title+description, upsert into pgvector. Returns upserted count.
     */
    public int indexByIds(List<String> cveIds) {
        if (cveIds == null || cveIds.isEmpty()) {
            return 0;
        }
        
        // Keep input order but skip null/blank
        List<String> wanted = new ArrayList<>();
        for (String id : cveIds) {
            if (id != null && !id.isBlank()) {
                wanted.add(id);
            }
        }
        if (wanted.isEmpty()) {
            return 0;
        }
        
        Map<String, CveForEmbedding> map = new LinkedHashMap<>(cveClient.getByIds(wanted));
        
        // Build texts to embed (title + description fallback)
        List<CveForEmbedding> docs = new ArrayList<>(map.size());
        List<String> texts = new ArrayList<>(map.size());
        for (String id : wanted) {
            CveForEmbedding d = map.get(id);
            if (d == null) {
                continue;
            }
            docs.add(d);
            
            String title = d.title() != null ? d.title() : d.cveId();
            String desc = d.description() != null ? d.description() : "";
            texts.add(title + "\n\n" + desc);
        }
        if (docs.isEmpty()) {
            return 0;
        }
        
        var vectors = embeddings.embedAll(texts);
        if (vectors == null || vectors.size() != docs.size()) {
            throw new IllegalStateException(
                    "Embedding count mismatch: " + (vectors == null ? 0 : vectors.size()) + " vs " + docs.size());
        }
        
        vectorRepo.upsertAll(docs, vectors);
        return docs.size();
    }
    
    public int indexNextBatch(int batchSize) {
        int target = (batchSize <= 0) ? 50 : batchSize;
        int pageSize = Math.min(Math.max(target, 1), 100); // cve-store caps size at 100
        
        int page = autoPageCursor.get();
        List<CveForEmbedding> docs = new ArrayList<>(target);
        
        // Safety: don't loop forever if data changes
        for (int guard = 0; guard < 10_000 && docs.size() < target; guard++) {
            var candidates = cveClient.findCandidatesForEmbeddingPage(page, pageSize);
            if (candidates == null || candidates.isEmpty()) {
                autoPageCursor.set(0);
                break;
            }
            
            boolean reachedTargetMidPage = false;
            for (CveForEmbedding d : candidates) {
                if (d == null || d.cveId() == null || d.cveId().isBlank()) {
                    continue;
                }
                if (!vectorRepo.existsByCveId(d.cveId())) {
                    docs.add(d);
                }
                if (docs.size() >= target) {
                    reachedTargetMidPage = true;
                    break;
                }
            }
            
            // If we stopped mid-page, keep the cursor on the same page so next run can pick up
            // remaining (not-yet-indexed) CVEs from this page.
            if (!reachedTargetMidPage) {
                page++;
            }
            autoPageCursor.set(page);
        }
        
        if (docs.isEmpty()) {
            return 0;
        }
        
        List<String> texts = new ArrayList<>(docs.size());
        for (CveForEmbedding d : docs) {
            String title = d.title() != null ? d.title() : d.cveId();
            String desc = d.description() != null ? d.description() : "";
            texts.add(title + "\n\n" + desc);
        }
        
        var vectors = embeddings.embedAll(texts);
        if (vectors == null || vectors.size() != docs.size()) {
            throw new IllegalStateException(
                    "Embedding count mismatch: " +
                            (vectors == null ? 0 : vectors.size()) +
                            " vs " + docs.size());
        }
        
        vectorRepo.upsertAll(docs, vectors);
        return docs.size();
    }
}
