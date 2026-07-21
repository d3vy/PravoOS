package com.pravoos.ai.core.internal.service;

import com.pravoos.ai.document.api.DocumentResponse;
import com.pravoos.ai.document.internal.model.entity.Document;
import com.pravoos.ai.document.internal.repository.jpa.DocumentChunkRepository;
import com.pravoos.ai.document.internal.repository.jpa.DocumentRepository;
import com.pravoos.ai.document.internal.service.DocumentService;
import com.pravoos.ai.document.internal.service.FileCryptoService;
import com.pravoos.ai.document.internal.service.MalwareScanClient;
import com.pravoos.ai.document.internal.service.UploadContentInspector;
import com.pravoos.ai.document.internal.service.UploadRateLimiter;
import com.pravoos.ai.shared.config.DocumentProperties;
import com.pravoos.ai.shared.exception.DocumentNotFoundException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentClientVisibilityTest {

    @Mock private DocumentRepository documentRepository;
    @Mock private DocumentChunkRepository documentChunkRepository;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private FileCryptoService fileCryptoService;
    @Mock private MalwareScanClient malwareScanClient;
    @Mock private UploadContentInspector uploadContentInspector;
    @Mock private UploadRateLimiter uploadRateLimiter;

    private DocumentService service;

    @BeforeEach
    void setUp() {
        DocumentProperties properties = new DocumentProperties(
                "/tmp/pravoos-test", 1000, 100, 5, 5000, 50, 0, 0, 60);
        service = new DocumentService(documentRepository, documentChunkRepository, eventPublisher,
                properties, fileCryptoService, malwareScanClient, uploadContentInspector, uploadRateLimiter,
                new SimpleMeterRegistry());
    }

    private Document caseDocument(UUID caseId, boolean visibleToClient) {
        Document document = new Document();
        document.setCaseId(caseId);
        document.setVisibleToClient(visibleToClient);
        document.setFilePath("/tmp/pravoos-test/missing/document.pdf");
        document.setFileName("doc.pdf");
        document.setFileType("pdf");
        return document;
    }

    @Test
    void loadClientContent_throwsNotFound_whenDocumentNotVisible() {
        UUID caseId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        when(documentRepository.findById(documentId)).thenReturn(Optional.of(caseDocument(caseId, false)));

        assertThatThrownBy(() -> service.loadClientContent(documentId, caseId))
                .isInstanceOf(DocumentNotFoundException.class);
        verify(fileCryptoService, never()).decryptFile(any());
    }

    @Test
    void loadClientContent_throwsNotFound_whenDocumentBelongsToAnotherCase() {
        UUID documentId = UUID.randomUUID();
        when(documentRepository.findById(documentId))
                .thenReturn(Optional.of(caseDocument(UUID.randomUUID(), true)));

        assertThatThrownBy(() -> service.loadClientContent(documentId, UUID.randomUUID()))
                .isInstanceOf(DocumentNotFoundException.class);
        verify(fileCryptoService, never()).decryptFile(any());
    }

    @Test
    void setClientVisibility_updatesFlag_whenDocumentBelongsToCase() {
        UUID caseId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        Document document = caseDocument(caseId, false);
        when(documentRepository.findById(documentId)).thenReturn(Optional.of(document));
        when(documentRepository.save(document)).thenReturn(document);

        DocumentResponse response = service.setClientVisibility(documentId, caseId, true);

        assertThat(document.isVisibleToClient()).isTrue();
        assertThat(response.visibleToClient()).isTrue();
    }

    @Test
    void setClientVisibility_throwsNotFound_whenDocumentBelongsToAnotherCase() {
        UUID documentId = UUID.randomUUID();
        when(documentRepository.findById(documentId))
                .thenReturn(Optional.of(caseDocument(UUID.randomUUID(), false)));

        assertThatThrownBy(() -> service.setClientVisibility(documentId, UUID.randomUUID(), true))
                .isInstanceOf(DocumentNotFoundException.class);
        verify(documentRepository, never()).save(any());
    }
}
