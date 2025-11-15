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

@Service
public class EmbeddingIndexService {
    private final CveStoreClient cveClient;
    private final EmbeddingsClient embeddings;
    private final VectorStoreRepository vectorRepo;
    
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
            // minimal concat; can be tuned later (truncate, sanitize, etc.)
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
}
