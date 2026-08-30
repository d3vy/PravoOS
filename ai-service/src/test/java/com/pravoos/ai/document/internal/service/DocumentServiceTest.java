package com.pravoos.ai.document.internal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.pravoos.ai.document.api.DocumentResponse;
import com.pravoos.ai.document.api.DocumentUploadResponse;
import com.pravoos.ai.document.internal.dto.LegislationResponse;
import com.pravoos.ai.document.internal.model.entity.Document;
import com.pravoos.ai.document.internal.pipeline.ChunkData;
import com.pravoos.ai.document.internal.repository.jpa.DocumentChunkRepository;
import com.pravoos.ai.document.internal.repository.jpa.DocumentRepository;
import com.pravoos.ai.recyclebin.api.BinSnapshot;
import com.pravoos.ai.shared.config.DocumentProperties;
import com.pravoos.ai.shared.exception.DocumentNotFoundException;
import com.pravoos.ai.shared.exception.DocumentProcessingException;
import com.pravoos.ai.shared.exception.StorageQuotaExceededException;
import com.pravoos.ai.shared.model.enums.DocumentKind;
import com.pravoos.ai.shared.model.enums.DocumentStatus;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class DocumentServiceTest {

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
    storageDir = Files.createTempDirectory("pravoos-document-service-test");
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
      try (var walk = Files.walk(storageDir)) {
        walk.sorted((a, b) -> b.compareTo(a)).forEach(p -> p.toFile().delete());
      }
    }
  }

  private static final byte[] PDF_BYTES = {0x25, 0x50, 0x44, 0x46, '-', '1', '.', '4'};

  private static byte[] docxBytes() {
    try {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      try (ZipOutputStream zip = new ZipOutputStream(out)) {
        zip.putNextEntry(new ZipEntry("[Content_Types].xml"));
        zip.write("<Types/>".getBytes());
        zip.closeEntry();
      }
      return out.toByteArray();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  void upload_throwsProcessingException_whenFileEmpty() {
    MockMultipartFile empty =
        new MockMultipartFile("file", "doc.pdf", "application/pdf", new byte[0]);
    UUID uploadedBy = UUID.randomUUID();

    assertThatThrownBy(() -> service.uploadKnowledgeBaseDocument(empty, "title", uploadedBy))
        .isInstanceOf(DocumentProcessingException.class);
    verifyNoInteractions(uploadRateLimiter, documentRepository);
  }

  @Test
  void upload_throwsProcessingException_whenFileIsNull() {
    assertThatThrownBy(() -> service.uploadKnowledgeBaseDocument(null, "title", UUID.randomUUID()))
        .isInstanceOf(DocumentProcessingException.class);
  }

  @Test
  void upload_throwsProcessingException_whenCaseLimitReached() {
    UUID uploadedBy = UUID.randomUUID();
    UUID caseId = UUID.randomUUID();
    MockMultipartFile file = new MockMultipartFile("file", "doc.pdf", "application/pdf", PDF_BYTES);
    when(documentRepository.countByCaseId(caseId)).thenReturn(2L);

    assertThatThrownBy(() -> service.upload(file, "title", uploadedBy, caseId))
        .isInstanceOf(DocumentProcessingException.class);
    verify(malwareScanClient, never()).scan(any(), anyString());
  }

  @Test
  void upload_throwsProcessingException_whenFileNameHasNoExtension() {
    MockMultipartFile file = new MockMultipartFile("file", "doc", "application/pdf", PDF_BYTES);

    assertThatThrownBy(() -> service.uploadKnowledgeBaseDocument(file, "title", UUID.randomUUID()))
        .isInstanceOf(DocumentProcessingException.class);
  }

  @Test
  void upload_throwsProcessingException_whenExtensionUnsupported() {
    MockMultipartFile file =
        new MockMultipartFile("file", "doc.exe", "application/x-msdownload", PDF_BYTES);

    assertThatThrownBy(() -> service.uploadKnowledgeBaseDocument(file, "title", UUID.randomUUID()))
        .isInstanceOf(DocumentProcessingException.class);
  }

  @Test
  void upload_throwsProcessingException_whenContentDoesNotMatchExtension() {
    MockMultipartFile file =
        new MockMultipartFile("file", "doc.pdf", "application/pdf", "not a pdf".getBytes());

    assertThatThrownBy(() -> service.uploadKnowledgeBaseDocument(file, "title", UUID.randomUUID()))
        .isInstanceOf(DocumentProcessingException.class);
  }

  @Test
  void upload_throwsProcessingException_whenDocxIsNotValidZipEntryContainer() {
    MockMultipartFile file =
        new MockMultipartFile(
            "file",
            "doc.docx",
            "application/vnd.openxmlformats",
            new byte[] {0x50, 0x4B, 0x03, 0x04});

    assertThatThrownBy(() -> service.uploadKnowledgeBaseDocument(file, "title", UUID.randomUUID()))
        .isInstanceOf(DocumentProcessingException.class);
  }

  @Test
  void upload_throwsStorageQuotaExceeded_whenLawyerDocumentLimitReached() throws IOException {
    DocumentProperties properties =
        new DocumentProperties(storageDir.toString(), 1000, 100, 5, 5000, 0, 1, 0, 0);
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
    UUID uploadedBy = UUID.randomUUID();
    MockMultipartFile file = new MockMultipartFile("file", "doc.pdf", "application/pdf", PDF_BYTES);
    when(documentRepository.countByUploadedBy(uploadedBy)).thenReturn(1L);

    assertThatThrownBy(() -> service.uploadKnowledgeBaseDocument(file, "title", uploadedBy))
        .isInstanceOf(StorageQuotaExceededException.class);
  }

  @Test
  void upload_throwsStorageQuotaExceeded_whenTotalBytesLimitExceeded() throws IOException {
    DocumentProperties properties =
        new DocumentProperties(storageDir.toString(), 1000, 100, 5, 5000, 0, 0, 4, 0);
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
    UUID uploadedBy = UUID.randomUUID();
    MockMultipartFile file = new MockMultipartFile("file", "doc.pdf", "application/pdf", PDF_BYTES);
    when(documentRepository.sumSizeBytesByUploadedBy(uploadedBy)).thenReturn(2L);

    assertThatThrownBy(() -> service.uploadKnowledgeBaseDocument(file, "title", uploadedBy))
        .isInstanceOf(StorageQuotaExceededException.class);
  }

  @Test
  void upload_persistsDocumentAndPublishesEvent_onSuccess() {
    UUID uploadedBy = UUID.randomUUID();
    MockMultipartFile file =
        new MockMultipartFile("file", "contract.pdf", "application/pdf", PDF_BYTES);
    when(documentRepository.save(any(Document.class))).thenAnswer(inv -> inv.getArgument(0));

    DocumentUploadResponse response =
        service.uploadKnowledgeBaseDocument(file, "My Title", uploadedBy);

    ArgumentCaptor<Document> captor = ArgumentCaptor.forClass(Document.class);
    verify(documentRepository).save(captor.capture());
    Document saved = captor.getValue();
    assertThat(saved.getTitle()).isEqualTo("My Title");
    assertThat(saved.getUploadedBy()).isEqualTo(uploadedBy);
    assertThat(saved.getFileName()).isEqualTo("contract.pdf");
    assertThat(saved.getFileType()).isEqualTo("pdf");
    assertThat(saved.getSizeBytes()).isEqualTo(PDF_BYTES.length);
    assertThat(response.title()).isEqualTo("My Title");
    verify(eventPublisher).publishEvent(any(Object.class));
    verify(fileCryptoService).encryptToFile(any(), any());
  }

  @Test
  void upload_resolvesTitleFromFileName_whenTitleBlank() {
    UUID uploadedBy = UUID.randomUUID();
    MockMultipartFile file =
        new MockMultipartFile("file", "my-contract.pdf", "application/pdf", PDF_BYTES);
    when(documentRepository.save(any(Document.class))).thenAnswer(inv -> inv.getArgument(0));

    DocumentUploadResponse response = service.uploadKnowledgeBaseDocument(file, "  ", uploadedBy);

    assertThat(response.title()).isEqualTo("my-contract");
  }

  @Test
  void upload_acceptsValidDocx() {
    UUID uploadedBy = UUID.randomUUID();
    MockMultipartFile file =
        new MockMultipartFile(
            "file", "contract.docx", "application/vnd.openxmlformats", docxBytes());
    when(documentRepository.save(any(Document.class))).thenAnswer(inv -> inv.getArgument(0));

    DocumentUploadResponse response =
        service.uploadKnowledgeBaseDocument(file, "title", uploadedBy);

    assertThat(response.fileName()).isEqualTo("contract.docx");
  }

  @Test
  void uploadChatAttachment_throwsProcessingException_whenFileEmpty() {
    MockMultipartFile empty =
        new MockMultipartFile("file", "doc.pdf", "application/pdf", new byte[0]);

    assertThatThrownBy(() -> service.uploadChatAttachment(empty, "title", UUID.randomUUID()))
        .isInstanceOf(DocumentProcessingException.class);
  }

  @Test
  void uploadChatAttachment_setsChatAttachmentKindAndNoCase() {
    MockMultipartFile file =
        new MockMultipartFile("file", "attachment.pdf", "application/pdf", PDF_BYTES);
    when(documentRepository.save(any(Document.class))).thenAnswer(inv -> inv.getArgument(0));

    service.uploadChatAttachment(file, "title", UUID.randomUUID());

    ArgumentCaptor<Document> captor = ArgumentCaptor.forClass(Document.class);
    verify(documentRepository).save(captor.capture());
    Document saved = captor.getValue();
    assertThat(saved.getDocumentKind()).isEqualTo(DocumentKind.CHAT_ATTACHMENT);
    assertThat(saved.getCaseId()).isNull();
    assertThat(saved.isVisibleToClient()).isFalse();
  }

  @Test
  void uploadLegislation_throwsProcessingException_whenActBlank() {
    MockMultipartFile file = new MockMultipartFile("file", "law.pdf", "application/pdf", PDF_BYTES);

    assertThatThrownBy(
            () ->
                service.uploadLegislation(
                    file, "  ", "15", LocalDate.now(), "title", UUID.randomUUID()))
        .isInstanceOf(DocumentProcessingException.class);
  }

  @Test
  void uploadLegislation_throwsProcessingException_whenArticleBlank() {
    MockMultipartFile file = new MockMultipartFile("file", "law.pdf", "application/pdf", PDF_BYTES);

    assertThatThrownBy(
            () ->
                service.uploadLegislation(
                    file, "ГК РФ", "", LocalDate.now(), "title", UUID.randomUUID()))
        .isInstanceOf(DocumentProcessingException.class);
  }

  @Test
  void uploadLegislation_throwsProcessingException_whenEditionDateMissing() {
    MockMultipartFile file = new MockMultipartFile("file", "law.pdf", "application/pdf", PDF_BYTES);

    assertThatThrownBy(
            () -> service.uploadLegislation(file, "ГК РФ", "15", null, "title", UUID.randomUUID()))
        .isInstanceOf(DocumentProcessingException.class);
  }

  @Test
  void uploadLegislation_supersedesExistingArticle_andPersistsNewOne() {
    MockMultipartFile file = new MockMultipartFile("file", "law.pdf", "application/pdf", PDF_BYTES);
    Document existing = new Document();
    when(documentRepository.findByDocumentKindAndActCanonicalAndArticleNumberAndSupersededFalse(
            DocumentKind.LEGISLATION, "ГК РФ", "15"))
        .thenReturn(Optional.of(existing));
    when(documentRepository.saveAndFlush(existing)).thenReturn(existing);
    when(documentRepository.save(any(Document.class))).thenAnswer(inv -> inv.getArgument(0));

    LocalDate editionDate = LocalDate.of(2024, 1, 1);
    DocumentUploadResponse response =
        service.uploadLegislation(
            file, "ГК РФ", "15", editionDate, "Статья 15 ГК РФ", UUID.randomUUID());

    assertThat(existing.isSuperseded()).isTrue();
    verify(documentRepository).saveAndFlush(existing);
    ArgumentCaptor<Document> captor = ArgumentCaptor.forClass(Document.class);
    verify(documentRepository).save(captor.capture());
    Document saved = captor.getValue();
    assertThat(saved.getDocumentKind()).isEqualTo(DocumentKind.LEGISLATION);
    assertThat(saved.getActCanonical()).isEqualTo("ГК РФ");
    assertThat(saved.getArticleNumber()).isEqualTo("15");
    assertThat(saved.getEditionDate()).isEqualTo(editionDate);
    assertThat(saved.getTitle()).isEqualTo("Статья 15 ГК РФ");
    assertThat(response).isNotNull();
  }

  @Test
  void uploadLegislation_autoTitleTruncatesAtFirstDot_whenTitleBlank() {
    MockMultipartFile file = new MockMultipartFile("file", "law.pdf", "application/pdf", PDF_BYTES);
    when(documentRepository.findByDocumentKindAndActCanonicalAndArticleNumberAndSupersededFalse(
            DocumentKind.LEGISLATION, "ГК РФ", "15"))
        .thenReturn(Optional.empty());
    when(documentRepository.save(any(Document.class))).thenAnswer(inv -> inv.getArgument(0));

    service.uploadLegislation(file, "ГК РФ", "15", LocalDate.of(2024, 1, 1), "", UUID.randomUUID());

    ArgumentCaptor<Document> captor = ArgumentCaptor.forClass(Document.class);
    verify(documentRepository).save(captor.capture());
    // resolveTitle() falls back to stripExtension("ст. 15 ГК РФ"), which cuts at the first
    // '.' — a pre-existing quirk of the auto-generated title, not fixed by this test.
    assertThat(captor.getValue().getTitle()).isEqualTo("ст");
  }

  @Test
  void completeProcessing_throwsNotFound_whenDocumentMissing() {
    UUID documentId = UUID.randomUUID();
    when(documentRepository.findById(documentId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.completeProcessing(documentId, List.of()))
        .isInstanceOf(DocumentNotFoundException.class);
  }

  @Test
  void completeProcessing_savesChunksAndMarksReady() {
    UUID documentId = UUID.randomUUID();
    Document document = new Document();
    when(documentRepository.findById(documentId)).thenReturn(Optional.of(document));
    List<ChunkData> chunks =
        List.of(
            new ChunkData("first", 0, new float[] {1f}),
            new ChunkData("second", 1, new float[] {2f}));

    service.completeProcessing(documentId, chunks);

    verify(documentChunkRepository).saveAll(argThat(list -> ((List<?>) list).size() == 2));
    assertThat(document.getStatus()).isEqualTo(DocumentStatus.READY);
    verify(documentRepository).save(document);
  }

  @Test
  void markFailed_doesNothing_whenDocumentMissing() {
    UUID documentId = UUID.randomUUID();
    when(documentRepository.findById(documentId)).thenReturn(Optional.empty());

    service.markFailed(documentId);

    verify(documentRepository, never()).save(any());
  }

  @Test
  void markFailed_setsFailedStatus_whenDocumentExists() {
    UUID documentId = UUID.randomUUID();
    Document document = new Document();
    when(documentRepository.findById(documentId)).thenReturn(Optional.of(document));

    service.markFailed(documentId);

    assertThat(document.getStatus()).isEqualTo(DocumentStatus.FAILED);
    verify(documentRepository).save(document);
  }

  @Test
  void requireDeletable_throwsNotFound_whenDocumentMissing() {
    UUID documentId = UUID.randomUUID();
    when(documentRepository.findById(documentId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.requireDeletable(documentId))
        .isInstanceOf(DocumentNotFoundException.class);
  }

  @Test
  void requireDeletable_throwsNotFound_whenDocumentBelongsToCase() {
    UUID documentId = UUID.randomUUID();
    Document document = new Document();
    document.setCaseId(UUID.randomUUID());
    when(documentRepository.findById(documentId)).thenReturn(Optional.of(document));

    assertThatThrownBy(() -> service.requireDeletable(documentId))
        .isInstanceOf(DocumentNotFoundException.class);
    verify(documentChunkRepository, never()).deleteByDocumentId(any());
  }

  @Test
  void moveToBin_marksDocumentDeletedAndKeepsChunks() {
    UUID documentId = UUID.randomUUID();
    Document document = new Document();
    ReflectionTestUtils.setField(document, "id", documentId);
    document.setTitle("Договор");
    document.setUploadedBy(UUID.randomUUID());
    when(documentRepository.findById(documentId)).thenReturn(Optional.of(document));

    BinSnapshot snapshot = service.moveToBin(documentId);

    assertThat(snapshot.entityId()).isEqualTo(documentId.toString());
    assertThat(snapshot.title()).isEqualTo("Договор");
    verify(documentRepository).softDelete(eq(documentId), any());
    verify(documentChunkRepository, never()).deleteByDocumentId(any());
  }

  @Test
  void purge_removesChunksAndDocumentRow() {
    UUID documentId = UUID.randomUUID();
    when(documentRepository.findFilePathIncludingDeleted(documentId)).thenReturn(Optional.empty());

    service.purge(documentId);

    verify(documentChunkRepository).deleteByDocumentId(documentId);
    verify(documentRepository).hardDelete(documentId);
  }

  @Test
  void purgeByCase_purgesEveryDocumentIncludingSoftDeleted() {
    UUID caseId = UUID.randomUUID();
    UUID first = UUID.randomUUID();
    UUID second = UUID.randomUUID();
    when(documentRepository.findIdsByCaseIdIncludingDeleted(caseId))
        .thenReturn(List.of(first, second));

    service.purgeByCase(caseId);

    verify(documentChunkRepository, times(2)).deleteByDocumentId(any());
    verify(documentRepository).hardDelete(first);
    verify(documentRepository).hardDelete(second);
  }

  @Test
  void findAll_mapsPageOfKnowledgeBaseDocuments() {
    Document document = new Document();
    when(documentRepository.findByCaseIdIsNullAndDocumentKindNotOrderByUploadedAtDesc(
            eq(DocumentKind.CHAT_ATTACHMENT), any()))
        .thenReturn(new PageImpl<>(List.of(document)));

    Page<DocumentResponse> result = service.findAll(0, 10);

    assertThat(result.getContent()).hasSize(1);
  }

  @Test
  void findChatAttachments_mapsList() {
    UUID lawyerId = UUID.randomUUID();
    Document document = new Document();
    when(documentRepository.findByUploadedByAndDocumentKindOrderByUploadedAtDesc(
            lawyerId, DocumentKind.CHAT_ATTACHMENT))
        .thenReturn(List.of(document));

    List<DocumentResponse> result = service.findChatAttachments(lawyerId);

    assertThat(result).hasSize(1);
  }

  @Test
  void findLegislation_mapsPage() {
    Document document = new Document();
    document.setActCanonical("ГК РФ");
    document.setArticleNumber("15");
    when(documentRepository.findByDocumentKindAndSupersededFalseOrderByEditionDateDesc(
            eq(DocumentKind.LEGISLATION), any()))
        .thenReturn(new PageImpl<>(List.of(document)));

    Page<LegislationResponse> result = service.findLegislation(0, 10);

    assertThat(result.getContent()).hasSize(1);
    assertThat(result.getContent().get(0).articleNumber()).isEqualTo("15");
  }

  @Test
  void loadContent_throwsNotFound_whenDocumentMissing() {
    UUID documentId = UUID.randomUUID();
    when(documentRepository.findById(documentId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.loadContent(documentId))
        .isInstanceOf(DocumentNotFoundException.class);
  }

  @Test
  void loadContent_throwsNotFound_whenDocumentBelongsToCase() {
    UUID documentId = UUID.randomUUID();
    Document document = new Document();
    document.setCaseId(UUID.randomUUID());
    when(documentRepository.findById(documentId)).thenReturn(Optional.of(document));

    assertThatThrownBy(() -> service.loadContent(documentId))
        .isInstanceOf(DocumentNotFoundException.class);
  }

  @Test
  void loadContent_throwsNotFound_whenFileMissingOnDisk() {
    UUID documentId = UUID.randomUUID();
    Document document = new Document();
    document.setFilePath(storageDir.resolve("missing.pdf").toString());
    when(documentRepository.findById(documentId)).thenReturn(Optional.of(document));

    assertThatThrownBy(() -> service.loadContent(documentId))
        .isInstanceOf(DocumentNotFoundException.class);
  }

  @Test
  void loadContent_decryptsAndReturnsContent() throws IOException {
    UUID documentId = UUID.randomUUID();
    Path filePath = storageDir.resolve("document.pdf");
    Files.write(filePath, PDF_BYTES);
    Document document = new Document();
    document.setFilePath(filePath.toString());
    document.setFileName("document.pdf");
    document.setFileType("pdf");
    when(documentRepository.findById(documentId)).thenReturn(Optional.of(document));
    when(fileCryptoService.decryptFile(filePath)).thenReturn(PDF_BYTES);

    var content = service.loadContent(documentId);

    assertThat(content.fileName()).isEqualTo("document.pdf");
    assertThat(content.contentLength()).isEqualTo(PDF_BYTES.length);
  }

  @Test
  void loadCaseContent_throwsNotFound_whenDocumentBelongsToDifferentCase() {
    UUID documentId = UUID.randomUUID();
    Document document = new Document();
    document.setCaseId(UUID.randomUUID());
    when(documentRepository.findById(documentId)).thenReturn(Optional.of(document));

    assertThatThrownBy(() -> service.loadCaseContent(documentId, UUID.randomUUID()))
        .isInstanceOf(DocumentNotFoundException.class);
  }

  @Test
  void caseRef_returnsRef_whenDocumentBelongsToCase() {
    UUID documentId = UUID.randomUUID();
    UUID caseId = UUID.randomUUID();
    UUID uploadedBy = UUID.randomUUID();
    Document document = new Document();
    document.setCaseId(caseId);
    document.setUploadedBy(uploadedBy);
    document.setTitle("Title");
    when(documentRepository.findById(documentId)).thenReturn(Optional.of(document));

    var ref = service.caseRef(documentId, caseId);

    assertThat(ref.caseId()).isEqualTo(caseId);
    assertThat(ref.uploadedBy()).isEqualTo(uploadedBy);
    assertThat(ref.title()).isEqualTo("Title");
  }

  @Test
  void clientVisibleRef_throwsNotFound_whenNotVisible() {
    UUID documentId = UUID.randomUUID();
    UUID caseId = UUID.randomUUID();
    Document document = new Document();
    document.setCaseId(caseId);
    document.setVisibleToClient(false);
    when(documentRepository.findById(documentId)).thenReturn(Optional.of(document));

    assertThatThrownBy(() -> service.clientVisibleRef(documentId, caseId))
        .isInstanceOf(DocumentNotFoundException.class);
  }

  @Test
  void contentSha256_throwsNotFound_whenFileMissingOnDisk() {
    UUID documentId = UUID.randomUUID();
    UUID caseId = UUID.randomUUID();
    Document document = new Document();
    document.setCaseId(caseId);
    document.setVisibleToClient(true);
    document.setFilePath(storageDir.resolve("missing.pdf").toString());
    when(documentRepository.findById(documentId)).thenReturn(Optional.of(document));

    assertThatThrownBy(() -> service.contentSha256(documentId, caseId))
        .isInstanceOf(DocumentNotFoundException.class);
  }

  @Test
  void caseContentSha256_computesHashOfDecryptedContent() throws IOException {
    UUID documentId = UUID.randomUUID();
    UUID caseId = UUID.randomUUID();
    Path filePath = storageDir.resolve("document.pdf");
    Files.write(filePath, PDF_BYTES);
    Document document = new Document();
    document.setCaseId(caseId);
    document.setFilePath(filePath.toString());
    when(documentRepository.findById(documentId)).thenReturn(Optional.of(document));
    when(fileCryptoService.decryptFile(filePath)).thenReturn(PDF_BYTES);

    String hash = service.caseContentSha256(documentId, caseId);

    assertThat(hash).hasSize(64);
  }

  @Test
  void findByCase_mapsList() {
    UUID caseId = UUID.randomUUID();
    when(documentRepository.findByCaseIdOrderByUploadedAtDesc(caseId))
        .thenReturn(List.of(new Document()));

    List<DocumentResponse> result = service.findByCase(caseId);

    assertThat(result).hasSize(1);
  }

  @Test
  void findClientVisibleByCase_mapsList() {
    UUID caseId = UUID.randomUUID();
    when(documentRepository.findByCaseIdAndVisibleToClientTrueOrderByUploadedAtDesc(caseId))
        .thenReturn(List.of(new Document()));

    List<DocumentResponse> result = service.findClientVisibleByCase(caseId);

    assertThat(result).hasSize(1);
  }
}
