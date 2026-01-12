package com.finki.vladislavangelovski.ai_service.indexing;

import com.finki.vladislavangelovski.ai_service.clients.cve.CveStoreClient;
import com.finki.vladislavangelovski.ai_service.embeddings.EmbeddingsClient;
import com.finki.vladislavangelovski.ai_service.vector.VectorStoreRepository;
import com.finki.vladislavangelovski.common.dto.CveForEmbedding;
import com.finki.vladislavangelovski.common.dto.EpssScoreDto;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class EmbeddingIndexService {
    private final CveStoreClient cveClient;
    private final EmbeddingsClient embeddings;
    private final VectorStoreRepository vectorRepo;
    
    /**
     * Dev-friendly cursor used only by the “index next batch” endpoint to move
     * past page 0 without the caller having to supply a page param.
     * <p>
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
    
    private static Double toDouble(BigDecimal bd) {
        return bd != null ? bd.doubleValue() : null;
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
        
        List<CveForEmbedding> enriched = new ArrayList<>(docs.size());
        for (CveForEmbedding d : docs) {
            if (d == null || d.cveId() == null || d.cveId().isBlank()) {
                continue;
            }
            try {
                EpssScoreDto epss = cveClient.getLatestEpss(d.cveId()).orElse(null);
                if (epss != null) {
                    enriched.add(new CveForEmbedding(d.cveId(), d.title(), d.description(), d.cwe(), d.cvssBase(),
                                                     d.cvssVector(), d.published(), d.lastModified(), d.references(),
                                                     toDouble(epss.getScore()), toDouble(epss.getPercentile())));
                }
                else {
                    enriched.add(d);
                }
            } catch (Exception ex) {
                enriched.add(d);
            }
        }
        docs = enriched;
        
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
        int pageSize = Math.min(Math.max(target, 1), 100);
        
        int page = autoPageCursor.get();
        List<CveForEmbedding> docs = new ArrayList<>(target);
        
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
            
            if (!reachedTargetMidPage) {
                page++;
            }
            autoPageCursor.set(page);
        }
        
        if (docs.isEmpty()) {
            return 0;
        }
        
        List<String> ids = new ArrayList<>(docs.size());
        for (CveForEmbedding d : docs) {
            ids.add(d.cveId());
        }
        
        Map<String, CveForEmbedding> enrichedMap = cveClient.getByIds(ids);
        List<CveForEmbedding> enrichedDocs = new ArrayList<>(docs.size());
        for (String id : ids) {
            CveForEmbedding e = (enrichedMap != null) ? enrichedMap.get(id) : null;
            enrichedDocs.add(e != null ? e : docs.stream().filter(x -> id.equals(x.cveId())).findFirst().orElse(null));
        }
        enrichedDocs.removeIf(Objects::isNull);
        
        if (enrichedDocs.isEmpty()) {
            return 0;
        }
        
        List<String> texts = new ArrayList<>(enrichedDocs.size());
        for (CveForEmbedding d : enrichedDocs) {
            String title = d.title() != null ? d.title() : d.cveId();
            String desc = d.description() != null ? d.description() : "";
            texts.add(title + "\n\n" + desc);
        }
        
        var vectors = embeddings.embedAll(texts);
        if (vectors == null || vectors.size() != enrichedDocs.size()) {
            throw new IllegalStateException(
                    "Embedding count mismatch: " + (vectors == null ? 0 : vectors.size()) + " vs " + enrichedDocs.size());
        }
        
        vectorRepo.upsertAll(enrichedDocs, vectors);
        return enrichedDocs.size();
    }
    
}
