package com.pravoos.ai.practice.internal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.pravoos.ai.document.api.DocumentCommand;
import com.pravoos.ai.document.api.DocumentContent;
import com.pravoos.ai.document.api.DocumentQuery;
import com.pravoos.ai.document.api.DocumentResponse;
import com.pravoos.ai.practice.internal.model.entity.Case;
import com.pravoos.ai.practice.internal.repository.jpa.CaseHearingEventRepository;
import com.pravoos.ai.practice.internal.repository.jpa.CaseRepository;
import com.pravoos.ai.shared.exception.CaseNotFoundException;
import com.pravoos.ai.shared.model.enums.CaseStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

@ExtendWith(MockitoExtension.class)
class PortalDocumentServiceTest {

  @Mock private CaseRepository caseRepository;
  @Mock private CaseHearingEventRepository hearingEventRepository;
  @Mock private DocumentCommand documentCommand;
  @Mock private DocumentQuery documentQuery;

  private PortalDocumentService service;

  @BeforeEach
  void setUp() {
    PortalCaseService portalCaseService =
        new PortalCaseService(caseRepository, hearingEventRepository);
    service = new PortalDocumentService(portalCaseService, documentCommand, documentQuery);
  }

  private Case caseWithClient(UUID clientId) {
    Case caseEntity = new Case();
    caseEntity.setClientId(clientId);
    caseEntity.setStatus(CaseStatus.IN_PROGRESS);
    return caseEntity;
  }

  @Test
  void listCaseDocuments_returnsVisibleDocs_whenInScope() {
    UUID clientId = UUID.randomUUID();
    UUID caseId = UUID.randomUUID();
    DocumentResponse doc =
        new DocumentResponse(UUID.randomUUID(), "Иск", "isk.pdf", "pdf", null, null, true);
    when(caseRepository.findById(caseId)).thenReturn(Optional.of(caseWithClient(clientId)));
    when(documentQuery.findClientVisibleByCase(caseId)).thenReturn(List.of(doc));

    assertThat(service.listCaseDocuments(caseId, List.of(clientId))).containsExactly(doc);
  }

  @Test
  void listCaseDocuments_throwsNotFound_whenCaseBelongsToAnotherClient() {
    UUID caseId = UUID.randomUUID();
    when(caseRepository.findById(caseId))
        .thenReturn(Optional.of(caseWithClient(UUID.randomUUID())));

    assertThatThrownBy(() -> service.listCaseDocuments(caseId, List.of(UUID.randomUUID())))
        .isInstanceOf(CaseNotFoundException.class);
    verifyNoInteractions(documentCommand, documentQuery);
  }

  @Test
  void downloadCaseDocument_returnsContent_whenInScope() {
    UUID clientId = UUID.randomUUID();
    UUID caseId = UUID.randomUUID();
    UUID documentId = UUID.randomUUID();
    DocumentContent content = new DocumentContent(null, "isk.pdf", "pdf", 10);
    when(caseRepository.findById(caseId)).thenReturn(Optional.of(caseWithClient(clientId)));
    when(documentCommand.loadClientContent(documentId, caseId)).thenReturn(content);

    assertThat(service.downloadCaseDocument(caseId, documentId, List.of(clientId)))
        .isSameAs(content);
  }

  @Test
  void downloadCaseDocument_throwsNotFound_whenCaseOutOfScope_withoutTouchingDocuments() {
    UUID caseId = UUID.randomUUID();
    UUID documentId = UUID.randomUUID();
    when(caseRepository.findById(caseId))
        .thenReturn(Optional.of(caseWithClient(UUID.randomUUID())));

    assertThatThrownBy(
            () -> service.downloadCaseDocument(caseId, documentId, List.of(UUID.randomUUID())))
        .isInstanceOf(CaseNotFoundException.class);
    verifyNoInteractions(documentCommand, documentQuery);
  }

  @Test
  void uploadCaseDocument_uploadsAsClientVisible_whenInScope() {
    UUID clientId = UUID.randomUUID();
    UUID caseId = UUID.randomUUID();
    UUID clientUserId = UUID.randomUUID();
    MultipartFile file = org.mockito.Mockito.mock(MultipartFile.class);
    when(caseRepository.findById(caseId)).thenReturn(Optional.of(caseWithClient(clientId)));

    service.uploadCaseDocument(caseId, file, "Договор", clientUserId, List.of(clientId));

    verify(documentCommand).upload(file, "Договор", clientUserId, caseId, true);
  }

  @Test
  void uploadCaseDocument_throwsNotFound_whenCaseOutOfScope_withoutUpload() {
    UUID caseId = UUID.randomUUID();
    when(caseRepository.findById(caseId))
        .thenReturn(Optional.of(caseWithClient(UUID.randomUUID())));

    assertThatThrownBy(
            () ->
                service.uploadCaseDocument(
                    caseId, null, "x", UUID.randomUUID(), List.of(UUID.randomUUID())))
        .isInstanceOf(CaseNotFoundException.class);
    verifyNoInteractions(documentCommand, documentQuery);
  }
}
