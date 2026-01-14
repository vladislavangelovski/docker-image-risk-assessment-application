package com.finki.vladislavangelovski.ai_service.indexing;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class StartupEmbeddingIndexer {
    private final EmbeddingIndexService indexService;
    private final TaskExecutor taskExecutor;
    private final boolean enabled;
    private final int batchSize;
    private final int maxBatches;
    
    public StartupEmbeddingIndexer(EmbeddingIndexService indexService,
                                   TaskExecutor taskExecutor,
                                   @Value("${embeddings.startup.enabled:false}") boolean enabled,
                                   @Value("${embeddings.startup.batch-size:20}") int batchSize,
                                   @Value("${embeddings.startup.max-batches:5}") int maxBatches) {
        this.indexService = indexService;
        this.taskExecutor = taskExecutor;
        this.enabled = enabled;
        this.batchSize = batchSize;
        this.maxBatches = maxBatches;
    }
    
    @EventListener(ApplicationReadyEvent.class)
    public void runOnceImmediately() {
        if (!enabled) {
            log.info("Startup embeddings indexing disabled (embeddings.startup.enabled=false)");
            return;
        }
        
        int safeBatchSize = Math.max(1, batchSize);
        int safeMaxBatches = Math.max(0, maxBatches);
        if (safeMaxBatches == 0) {
            log.warn("Startup embeddings indexing enabled, but max-batches=0; nothing to do");
            return;
        }
        
        taskExecutor.execute(() -> {
            int totalUpserted = 0;
            int batches = 0;
            
            log.info("Startup embeddings indexing: batchSize={}, maxBatches={}", safeBatchSize, safeMaxBatches);
            
            for (int i = 0; i < safeMaxBatches; i++) {
                int upserted;
                try {
                    upserted = indexService.indexNextBatch(safeBatchSize);
                } catch (Exception ex) {
                    log.error("Startup embeddings indexing failed on batch {}", i + 1, ex);
                    break;
                }
                
                if (upserted <= 0) {
                    log.info("Startup embeddings indexing: no more candidates, stopping");
                    break;
                }
                
                totalUpserted += upserted;
                batches++;
                
                if (upserted < safeBatchSize) {
                    log.info("Startup embeddings indexing: short batch ({} < {}), stopping", upserted, safeBatchSize);
                    break;
                }
            }
            
            log.info("Startup embeddings indexing complete: upserted {} across {} batches", totalUpserted, batches);
        });
    }
}
