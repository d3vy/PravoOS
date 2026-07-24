package com.pravoos.ai.document.internal.pipeline;

import com.pravoos.ai.document.internal.event.DocumentCreatedSpringEvent;
import com.pravoos.ai.document.internal.model.entity.Document;
import com.pravoos.ai.document.internal.repository.jpa.DocumentRepository;
import com.pravoos.ai.document.internal.service.DocumentService;
import com.pravoos.ai.document.internal.service.EmbeddingService;
import com.pravoos.ai.document.internal.service.FileCryptoService;
import com.pravoos.ai.llm.api.EmbeddingResult;
import com.pravoos.ai.shared.config.DocumentProperties;
import com.pravoos.ai.shared.exception.DocumentNotFoundException;
import com.pravoos.ai.shared.service.LlmQuotaService;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class EmbeddingPipeline {

  private static final Logger log = LoggerFactory.getLogger(EmbeddingPipeline.class);
  private static final int EMBEDDING_BATCH_SIZE = 64;

  private final DocumentRepository documentRepository;
  private final DocumentService documentService;
  private final DocumentParser documentParser;
  private final FileCryptoService fileCryptoService;
  private final TextChunker textChunker;
  private final EmbeddingService embeddingService;
  private final DocumentProperties documentProperties;
  private final LlmQuotaService llmQuotaService;

  public EmbeddingPipeline(
      DocumentRepository documentRepository,
      DocumentService documentService,
      DocumentParser documentParser,
      FileCryptoService fileCryptoService,
      TextChunker textChunker,
      EmbeddingService embeddingService,
      DocumentProperties documentProperties,
      LlmQuotaService llmQuotaService) {
    this.documentRepository = documentRepository;
    this.documentService = documentService;
    this.documentParser = documentParser;
    this.fileCryptoService = fileCryptoService;
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
      Document document =
          documentRepository
              .findById(documentId)
              .orElseThrow(() -> new DocumentNotFoundException(documentId));

      byte[] content = fileCryptoService.decryptFile(Paths.get(document.getFilePath()));
      String text = documentParser.extractText(content, document.getFileType());

      List<String> chunkTexts =
          textChunker.chunk(
              text, documentProperties.chunkSize(), documentProperties.chunkOverlap());

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
      log.info(
          "Document {} processed: {} chunks created, {} embedding tokens billed to {}",
          documentId,
          chunkData.size(),
          embeddingTokens,
          document.getUploadedBy());
    } catch (Exception e) {
      log.error("Embedding pipeline failed for document: {}", documentId, e);
      documentService.markFailed(documentId);
    }
  }
}
