package com.pravoos.ai.document.internal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pravoos.ai.document.internal.model.entity.Document;
import com.pravoos.ai.document.internal.repository.jpa.DocumentChunkRepository;
import com.pravoos.ai.document.internal.repository.jpa.DocumentRepository;
import com.pravoos.ai.shared.config.DocumentProperties;
import com.pravoos.ai.shared.model.enums.DocumentKind;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.web.MockMultipartFile;

/**
 * Guards the boundary between the shared knowledge base and case documents. A document with no case
 * is readable by every lawyer (see {@code DocumentAccessGuard}), so it may only be written by the
 * admin-only {@code /api/ai/documents} route — the role matcher itself is covered by {@code
 * SecurityRouteMatrixTest}.
 */
@ExtendWith(MockitoExtension.class)
class KnowledgeBaseIsolationTest {

  private static final byte[] PDF_BYTES = {0x25, 0x50, 0x44, 0x46, '-', '1', '.', '4'};

  private static final Set<String> KNOWLEDGE_BASE_WRITERS =
      Set.of("DocumentService.java", "DocumentController.java");

  @Mock private DocumentRepository documentRepository;
  @Mock private DocumentChunkRepository documentChunkRepository;
  @Mock private ApplicationEventPublisher eventPublisher;
  @Mock private FileCryptoService fileCryptoService;
  @Mock private MalwareScanClient malwareScanClient;
  @Mock private UploadContentInspector uploadContentInspector;
  @Mock private UploadRateLimiter uploadRateLimiter;

  private DocumentService service;
  private Path storageDir;

  @BeforeEach
  void setUp() throws IOException {
    storageDir = Files.createTempDirectory("pravoos-knowledge-base-test");
    DocumentProperties properties =
        new DocumentProperties(storageDir.toString(), 1000, 100, 5, 5000, 2, 0, 0, 0);
    service =
        new DocumentService(
            documentRepository,
            documentChunkRepository,
            new DocumentUploadWriter(documentRepository, eventPublisher),
            properties,
            fileCryptoService,
            malwareScanClient,
            uploadContentInspector,
            uploadRateLimiter,
            new SimpleMeterRegistry());
  }

  @AfterEach
  void tearDown() throws IOException {
    if (Files.exists(storageDir)) {
      try (Stream<Path> walk = Files.walk(storageDir)) {
        walk.sorted((left, right) -> right.compareTo(left)).forEach(path -> path.toFile().delete());
      }
    }
  }

  @Test
  void knowledgeBaseUpload_storesDocumentWithoutCase() {
    MockMultipartFile file =
        new MockMultipartFile("file", "gk-rf.pdf", "application/pdf", PDF_BYTES);
    when(documentRepository.save(any(Document.class))).thenAnswer(inv -> inv.getArgument(0));

    service.uploadKnowledgeBaseDocument(file, "ГК РФ", UUID.randomUUID());

    ArgumentCaptor<Document> captor = ArgumentCaptor.forClass(Document.class);
    verify(documentRepository).save(captor.capture());
    assertThat(captor.getValue().getCaseId()).isNull();
    assertThat(captor.getValue().getDocumentKind()).isEqualTo(DocumentKind.GENERAL);
  }

  @Test
  void caseUpload_rejectsNullCaseId() {
    MockMultipartFile file =
        new MockMultipartFile("file", "contract.pdf", "application/pdf", PDF_BYTES);

    assertThatThrownBy(() -> service.upload(file, "Договор", UUID.randomUUID(), null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("uploadKnowledgeBaseDocument");
  }

  @Test
  void caseUpload_keepsDocumentAttachedToItsCase() {
    UUID caseId = UUID.randomUUID();
    MockMultipartFile file =
        new MockMultipartFile("file", "contract.pdf", "application/pdf", PDF_BYTES);
    when(documentRepository.save(any(Document.class))).thenAnswer(inv -> inv.getArgument(0));

    service.upload(file, "Договор", UUID.randomUUID(), caseId);

    ArgumentCaptor<Document> captor = ArgumentCaptor.forClass(Document.class);
    verify(documentRepository).save(captor.capture());
    assertThat(captor.getValue().getCaseId()).isEqualTo(caseId);
  }

  @Test
  void knowledgeBaseUpload_isReachableOnlyFromTheAdminController() throws IOException {
    try (Stream<Path> sources = Files.walk(Path.of("src", "main", "java"))) {
      List<String> callers =
          sources
              .filter(path -> path.toString().endsWith(".java"))
              .filter(KnowledgeBaseIsolationTest::mentionsKnowledgeBaseUpload)
              .map(path -> path.getFileName().toString())
              .toList();

      assertThat(callers).containsExactlyInAnyOrderElementsOf(KNOWLEDGE_BASE_WRITERS);
    }
  }

  private static boolean mentionsKnowledgeBaseUpload(Path source) {
    try {
      return Files.readString(source).contains("uploadKnowledgeBaseDocument");
    } catch (IOException e) {
      throw new IllegalStateException("Cannot read " + source, e);
    }
  }
}
