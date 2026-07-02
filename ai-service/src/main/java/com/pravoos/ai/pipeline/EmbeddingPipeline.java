package com.pravoos.ai.pipeline;

import com.pravoos.ai.config.DocumentProperties;
import com.pravoos.ai.event.DocumentCreatedSpringEvent;
import com.pravoos.ai.exception.DocumentNotFoundException;
import com.pravoos.ai.llm.EmbeddingResult;
import com.pravoos.ai.model.entity.Document;
import com.pravoos.ai.repository.jpa.DocumentRepository;
import com.pravoos.ai.service.DocumentService;
import com.pravoos.ai.service.EmbeddingService;
import com.pravoos.ai.service.LlmQuotaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
public class EmbeddingPipeline {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingPipeline.class);
    private static final int EMBEDDING_BATCH_SIZE = 64;

    private final DocumentRepository documentRepository;
    private final DocumentService documentService;
    private final DocumentParser documentParser;
    private final TextChunker textChunker;
    private final EmbeddingService embeddingService;
    private final DocumentProperties documentProperties;
    private final LlmQuotaService llmQuotaService;

    public EmbeddingPipeline(DocumentRepository documentRepository,
                             DocumentService documentService,
                             DocumentParser documentParser,
                             TextChunker textChunker,
                             EmbeddingService embeddingService,
                             DocumentProperties documentProperties,
                             LlmQuotaService llmQuotaService) {
        this.documentRepository = documentRepository;
        this.documentService = documentService;
        this.documentParser = documentParser;
        this.textChunker = textChunker;
        this.embeddingService = embeddingService;
        this.documentProperties = documentProperties;
        this.llmQuotaService = llmQuotaService;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDocumentCreated(DocumentCreatedSpringEvent event) {
        UUID documentId = event.documentId();
        log.info("Starting embedding pipeline for document: {}", documentId);

        try {
            Document document = documentRepository.findById(documentId)
                    .orElseThrow(() -> new DocumentNotFoundException(documentId));

            String text = documentParser.extractText(
                    Paths.get(document.getFilePath()),
                    document.getFileType()
            );

            List<String> chunkTexts = textChunker.chunk(
                    text,
                    documentProperties.chunkSize(),
                    documentProperties.chunkOverlap()
            );

            List<ChunkData> chunkData = new ArrayList<>(chunkTexts.size());
            long embeddingTokens = 0L;
            for (int start = 0; start < chunkTexts.size(); start += EMBEDDING_BATCH_SIZE) {
                int end = Math.min(start + EMBEDDING_BATCH_SIZE, chunkTexts.size());
                List<String> batch = chunkTexts.subList(start, end);
                EmbeddingResult result = embeddingService.embedBatch(batch);
                embeddingTokens += result.totalTokens();
                for (int i = 0; i < batch.size(); i++) {
                    chunkData.add(new ChunkData(batch.get(i), start + i, result.embeddings().get(i)));
                }
            }

            documentService.completeProcessing(documentId, chunkData);
            llmQuotaService.recordTokenUsage(document.getUploadedBy(), embeddingTokens);
            log.info("Document {} processed: {} chunks created, {} embedding tokens billed to {}",
                    documentId, chunkData.size(), embeddingTokens, document.getUploadedBy());
        } catch (Exception e) {
            log.error("Embedding pipeline failed for document: {}", documentId, e);
            documentService.markFailed(documentId);
        }
    }
}
