package com.pravoos.ai.document.internal.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.pravoos.ai.document.internal.event.DocumentCreatedSpringEvent;
import com.pravoos.ai.document.internal.model.entity.Document;
import com.pravoos.ai.document.internal.repository.jpa.DocumentRepository;
import com.pravoos.ai.document.internal.service.DocumentService;
import com.pravoos.ai.document.internal.service.DocumentSummaryService;
import com.pravoos.ai.document.internal.service.EmbeddingService;
import com.pravoos.ai.document.internal.service.FileCryptoService;
import com.pravoos.ai.llm.api.EmbeddingResult;
import com.pravoos.ai.shared.config.DocumentProperties;
import com.pravoos.ai.shared.service.LlmQuotaService;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EmbeddingPipelineTest {

  @Mock private DocumentRepository documentRepository;
  @Mock private DocumentService documentService;
  @Mock private DocumentParser documentParser;
  @Mock private FileCryptoService fileCryptoService;
  @Mock private TextChunker textChunker;
  @Mock private EmbeddingService embeddingService;
  @Mock private LlmQuotaService llmQuotaService;
  @Mock private DocumentSummaryService documentSummaryService;

  private EmbeddingPipeline pipeline;

  @BeforeEach
  void setUp() {
    DocumentProperties properties = new DocumentProperties("/tmp", 1000, 100, 5, 5000, 0, 0, 0, 0);
    pipeline =
        new EmbeddingPipeline(
            documentRepository,
            documentService,
            documentParser,
            fileCryptoService,
            textChunker,
            embeddingService,
            properties,
            llmQuotaService,
            documentSummaryService);
  }

  private Document document(UUID uploadedBy) {
    Document document = new Document();
    document.setFilePath("/tmp/doc.pdf");
    document.setFileType("pdf");
    document.setUploadedBy(uploadedBy);
    return document;
  }

  @Test
  void onDocumentCreated_marksFailed_whenDocumentMissing() {
    UUID documentId = UUID.randomUUID();
    when(documentRepository.findById(documentId)).thenReturn(Optional.empty());

    pipeline.onDocumentCreated(new DocumentCreatedSpringEvent(documentId));

    verify(documentService).markFailed(documentId);
    verify(documentService, never()).completeProcessing(any(), any());
  }

  @Test
  void onDocumentCreated_marksFailed_whenParsingThrows() {
    UUID documentId = UUID.randomUUID();
    UUID uploadedBy = UUID.randomUUID();
    when(documentRepository.findById(documentId)).thenReturn(Optional.of(document(uploadedBy)));
    when(fileCryptoService.decryptFile(any())).thenReturn(new byte[] {1});
    when(documentParser.extractText(any(), eq("pdf"))).thenThrow(new RuntimeException("boom"));

    pipeline.onDocumentCreated(new DocumentCreatedSpringEvent(documentId));

    verify(documentService).markFailed(documentId);
    verify(embeddingService, never()).embedBatch(anyList());
  }

  @Test
  void onDocumentCreated_completesProcessing_andRecordsTokenUsage_onSuccess() {
    UUID documentId = UUID.randomUUID();
    UUID uploadedBy = UUID.randomUUID();
    when(documentRepository.findById(documentId)).thenReturn(Optional.of(document(uploadedBy)));
    when(fileCryptoService.decryptFile(any())).thenReturn(new byte[] {1});
    when(documentParser.extractText(any(), eq("pdf"))).thenReturn("extracted text");
    when(textChunker.chunk("extracted text", 1000, 100)).thenReturn(List.of("chunk1", "chunk2"));
    when(embeddingService.embedBatch(List.of("chunk1", "chunk2")))
        .thenReturn(new EmbeddingResult(List.of(new float[] {1f}, new float[] {2f}), 42));

    pipeline.onDocumentCreated(new DocumentCreatedSpringEvent(documentId));

    ArgumentCaptor<List<ChunkData>> captor = ArgumentCaptor.forClass(List.class);
    verify(documentService).completeProcessing(eq(documentId), captor.capture());
    List<ChunkData> chunks = captor.getValue();
    assertThat(chunks).hasSize(2);
    assertThat(chunks.get(0).content()).isEqualTo("chunk1");
    assertThat(chunks.get(0).index()).isEqualTo(0);
    assertThat(chunks.get(1).index()).isEqualTo(1);
    verify(llmQuotaService).recordTokenUsage(uploadedBy, 42L);
    verify(documentSummaryService).summarizeAfterUpload(documentId, "extracted text");
    verify(documentService, never()).markFailed(any());
  }

  @Test
  void onDocumentCreated_batchesEmbeddingCallsBySixtyFour() {
    UUID documentId = UUID.randomUUID();
    UUID uploadedBy = UUID.randomUUID();
    List<String> manyChunks = new java.util.ArrayList<>();
    for (int i = 0; i < 70; i++) {
      manyChunks.add("chunk" + i);
    }
    when(documentRepository.findById(documentId)).thenReturn(Optional.of(document(uploadedBy)));
    when(fileCryptoService.decryptFile(any())).thenReturn(new byte[] {1});
    when(documentParser.extractText(any(), eq("pdf"))).thenReturn("text");
    when(textChunker.chunk("text", 1000, 100)).thenReturn(manyChunks);
    when(embeddingService.embedBatch(anyList()))
        .thenAnswer(
            inv -> {
              List<String> batch = inv.getArgument(0);
              List<float[]> embeddings = batch.stream().map(s -> new float[] {1f}).toList();
              return new EmbeddingResult(embeddings, 1);
            });

    pipeline.onDocumentCreated(new DocumentCreatedSpringEvent(documentId));

    verify(embeddingService, times(2)).embedBatch(anyList());
    ArgumentCaptor<List<ChunkData>> captor = ArgumentCaptor.forClass(List.class);
    verify(documentService).completeProcessing(eq(documentId), captor.capture());
    assertThat(captor.getValue()).hasSize(70);
    assertThat(captor.getValue().get(69).index()).isEqualTo(69);
  }
}
