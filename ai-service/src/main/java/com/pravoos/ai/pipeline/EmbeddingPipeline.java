package com.pravoos.ai.pipeline;

import com.pravoos.ai.config.DocumentProperties;
import com.pravoos.ai.event.DocumentCreatedSpringEvent;
import com.pravoos.ai.exception.DocumentNotFoundException;
import com.pravoos.ai.model.entity.Document;
import com.pravoos.ai.model.entity.DocumentChunk;
import com.pravoos.ai.model.enums.DocumentStatus;
import com.pravoos.ai.repository.DocumentChunkRepository;
import com.pravoos.ai.repository.DocumentRepository;
import com.pravoos.ai.service.EmbeddingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
public class EmbeddingPipeline {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingPipeline.class);

    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository documentChunkRepository;
    private final DocumentParser documentParser;
    private final TextChunker textChunker;
    private final EmbeddingService embeddingService;
    private final DocumentProperties documentProperties;

    public EmbeddingPipeline(DocumentRepository documentRepository,
                              DocumentChunkRepository documentChunkRepository,
                              DocumentParser documentParser,
                              TextChunker textChunker,
                              EmbeddingService embeddingService,
                              DocumentProperties documentProperties) {
        this.documentRepository = documentRepository;
        this.documentChunkRepository = documentChunkRepository;
        this.documentParser = documentParser;
        this.textChunker = textChunker;
        this.embeddingService = embeddingService;
        this.documentProperties = documentProperties;
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDocumentCreated(DocumentCreatedSpringEvent event) {
        UUID documentId = event.documentId();
        log.info("Starting embedding pipeline for document: {}", documentId);

        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new DocumentNotFoundException(documentId));

        try {
            String text = documentParser.extractText(
                    Paths.get(document.getFilePath()),
                    document.getFileType()
            );

            List<String> chunkTexts = textChunker.chunk(
                    text,
                    documentProperties.chunkSize(),
                    documentProperties.chunkOverlap()
            );

            List<DocumentChunk> chunks = new ArrayList<>();
            for (int i = 0; i < chunkTexts.size(); i++) {
                String chunkText = chunkTexts.get(i);
                float[] embedding = embeddingService.embed(chunkText);

                DocumentChunk chunk = new DocumentChunk();
                chunk.setDocument(document);
                chunk.setContent(chunkText);
                chunk.setChunkIndex(i);
                chunk.setEmbedding(embedding);
                chunks.add(chunk);
            }

            documentChunkRepository.saveAll(chunks);
            document.setStatus(DocumentStatus.READY);
            documentRepository.save(document);

            log.info("Document {} processed: {} chunks created", documentId, chunks.size());
        } catch (Exception e) {
            log.error("Embedding pipeline failed for document: {}", documentId, e);
            document.setStatus(DocumentStatus.FAILED);
            documentRepository.save(document);
        }
    }
}
