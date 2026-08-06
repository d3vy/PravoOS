package com.pravoos.ai.practice.internal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pravoos.ai.core.api.AiResponseDto;
import com.pravoos.ai.core.api.AiResponseQuery;
import com.pravoos.ai.core.api.SourceReference;
import com.pravoos.ai.document.api.DocumentQuery;
import com.pravoos.ai.document.api.DocumentResponse;
import com.pravoos.ai.practice.internal.dto.CaseExportModel;
import com.pravoos.ai.practice.internal.dto.ExportedFile;
import com.pravoos.ai.practice.internal.model.entity.Case;
import com.pravoos.ai.practice.internal.model.entity.CaseDraft;
import com.pravoos.ai.practice.internal.model.entity.CaseTask;
import com.pravoos.ai.practice.internal.model.entity.Client;
import com.pravoos.ai.practice.internal.repository.jpa.CaseDraftRepository;
import com.pravoos.ai.practice.internal.repository.jpa.CaseTaskRepository;
import com.pravoos.ai.practice.internal.repository.jpa.ClientRepository;
import com.pravoos.ai.shared.exception.InvalidExportFormatException;
import com.pravoos.ai.shared.model.enums.CaseStatus;
import com.pravoos.ai.shared.model.enums.ClientType;
import com.pravoos.ai.shared.model.enums.DocumentStatus;
import java.lang.reflect.Field;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
class CaseExportServiceTest {

  @Mock private CaseService caseService;
  @Mock private ClientRepository clientRepository;
  @Mock private DocumentQuery documentQuery;
  @Mock private AiResponseQuery aiResponseQuery;
  @Mock private CaseDraftRepository caseDraftRepository;
  @Mock private CaseTaskRepository caseTaskRepository;
  @Mock private CaseDocxWriter docxWriter;
  @Mock private CasePdfWriter pdfWriter;

  private CaseExportService service;

  private final UUID lawyerId = UUID.randomUUID();
  private final UUID caseId = UUID.randomUUID();
  private final UUID clientId = UUID.randomUUID();
  private final List<UUID> orgIds = List.of();

  @BeforeEach
  void setUp() {
    service =
        new CaseExportService(
            caseService,
            clientRepository,
            documentQuery,
            aiResponseQuery,
            caseDraftRepository,
            caseTaskRepository,
            docxWriter,
            pdfWriter);
  }

  private Case caseEntity(String title, UUID withClientId) {
    Case caseEntity = new Case();
    setId(caseEntity, caseId);
    caseEntity.setTitle(title);
    caseEntity.setDescription("Описание дела");
    caseEntity.setStatus(CaseStatus.IN_PROGRESS);
    caseEntity.setClientId(withClientId);
    return caseEntity;
  }

  private void setId(Case caseEntity, UUID id) {
    try {
      Field field = Case.class.getDeclaredField("id");
      field.setAccessible(true);
      field.set(caseEntity, id);
    } catch (ReflectiveOperationException e) {
      throw new RuntimeException(e);
    }
  }

  private void mockEmptyCollections() {
    when(documentQuery.findByCase(caseId)).thenReturn(List.of());
    when(caseTaskRepository.findByCaseIdOrderByDoneAscCreatedAtAsc(caseId)).thenReturn(List.of());
    when(aiResponseQuery.listByCase(caseId)).thenReturn(List.of());
    when(caseDraftRepository.findByCaseIdOrderByCreatedAtDesc(caseId)).thenReturn(List.of());
  }

  @Test
  void exportUsesDocxWriterForDocxFormat() {
    Case caseEntity = caseEntity("Дело №1", null);
    when(caseService.requireVisibleCase(caseId, lawyerId, orgIds)).thenReturn(caseEntity);
    mockEmptyCollections();
    when(docxWriter.write(any())).thenReturn(new byte[] {1, 2, 3});

    ExportedFile result = service.export(caseId, lawyerId, "DOCX", orgIds);

    assertThat(result.content()).isEqualTo(new byte[] {1, 2, 3});
    assertThat(result.contentType())
        .isEqualTo("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
    assertThat(result.fileName()).isEqualTo("Дело_Дело_1.docx");
    verify(pdfWriter, never()).write(any());
  }

  @Test
  void exportUsesPdfWriterForPdfFormat() {
    Case caseEntity = caseEntity("Дело №1", null);
    when(caseService.requireVisibleCase(caseId, lawyerId, orgIds)).thenReturn(caseEntity);
    mockEmptyCollections();
    when(pdfWriter.write(any())).thenReturn(new byte[] {4, 5});

    ExportedFile result = service.export(caseId, lawyerId, "PDF", orgIds);

    assertThat(result.content()).isEqualTo(new byte[] {4, 5});
    assertThat(result.contentType()).isEqualTo("application/pdf");
    assertThat(result.fileName()).isEqualTo("Дело_Дело_1.pdf");
    verify(docxWriter, never()).write(any());
  }

  @Test
  void exportDefaultsToDocxWhenFormatBlank() {
    Case caseEntity = caseEntity("Дело №1", null);
    when(caseService.requireVisibleCase(caseId, lawyerId, orgIds)).thenReturn(caseEntity);
    mockEmptyCollections();
    when(docxWriter.write(any())).thenReturn(new byte[] {1});

    service.export(caseId, lawyerId, null, orgIds);

    verify(docxWriter).write(any());
  }

  @Test
  void exportThrowsOnInvalidFormat() {
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> service.export(caseId, lawyerId, "XML", orgIds))
        .isInstanceOf(InvalidExportFormatException.class);
    verify(caseService, never()).requireVisibleCase(any(), any(), any());
  }

  @Test
  void assemblesClientSectionWhenClientPresent() {
    Case caseEntity = caseEntity("Дело №1", clientId);
    Client client = new Client();
    client.setName("Иванов И.И.");
    client.setType(ClientType.INDIVIDUAL);
    client.setPhone("+79990000000");
    client.setEmail("client@example.com");
    client.setInn("770123456789");
    client.setNotes("VIP");
    when(caseService.requireVisibleCase(caseId, lawyerId, orgIds)).thenReturn(caseEntity);
    when(clientRepository.findById(clientId)).thenReturn(Optional.of(client));
    mockEmptyCollections();

    ArgumentCaptor<CaseExportModel> captor = ArgumentCaptor.forClass(CaseExportModel.class);
    when(docxWriter.write(captor.capture())).thenReturn(new byte[] {0});

    service.export(caseId, lawyerId, "DOCX", orgIds);

    CaseExportModel model = captor.getValue();
    assertThat(model.client()).isNotNull();
    assertThat(model.client().name()).isEqualTo("Иванов И.И.");
    assertThat(model.client().type()).isEqualTo("Физлицо");
  }

  @Test
  void assemblesNullClientSectionWhenClientIdAbsent() {
    Case caseEntity = caseEntity("Дело №1", null);
    when(caseService.requireVisibleCase(caseId, lawyerId, orgIds)).thenReturn(caseEntity);
    mockEmptyCollections();
    ArgumentCaptor<CaseExportModel> captor = ArgumentCaptor.forClass(CaseExportModel.class);
    when(docxWriter.write(captor.capture())).thenReturn(new byte[] {0});

    service.export(caseId, lawyerId, "DOCX", orgIds);

    assertThat(captor.getValue().client()).isNull();
    verify(clientRepository, never()).findById(any());
  }

  @Test
  void assemblesDocumentTaskResponseAndDraftSections() {
    Case caseEntity = caseEntity("Дело №1", null);
    when(caseService.requireVisibleCase(caseId, lawyerId, orgIds)).thenReturn(caseEntity);

    when(documentQuery.findByCase(caseId))
        .thenReturn(
            List.of(
                new DocumentResponse(
                    UUID.randomUUID(),
                    "Договор",
                    "contract.pdf",
                    "pdf",
                    DocumentStatus.READY,
                    LocalDateTime.of(2026, 1, 1, 10, 0),
                    false)));

    CaseTask task = new CaseTask();
    task.setText("Подать иск");
    task.setDone(true);
    task.setDueDate(LocalDate.of(2026, 2, 1));
    when(caseTaskRepository.findByCaseIdOrderByDoneAscCreatedAtAsc(caseId))
        .thenReturn(List.of(task));

    when(aiResponseQuery.listByCase(caseId))
        .thenReturn(
            List.of(
                new AiResponseDto(
                    UUID.randomUUID(),
                    caseId,
                    "workflow-id",
                    "Проверка документов",
                    "Вопрос?",
                    "Ответ.",
                    List.of(new SourceReference("Источник 1", "фрагмент")),
                    null,
                    null,
                    LocalDateTime.of(2026, 1, 2, 12, 0),
                    List.of())));

    CaseDraft draft = new CaseDraft();
    draft.setDraftType("COMPLAINT");
    draft.setTitle("Черновик жалобы");
    draft.setContent("Текст черновика");
    when(caseDraftRepository.findByCaseIdOrderByCreatedAtDesc(caseId)).thenReturn(List.of(draft));

    ArgumentCaptor<CaseExportModel> captor = ArgumentCaptor.forClass(CaseExportModel.class);
    when(docxWriter.write(captor.capture())).thenReturn(new byte[] {0});

    service.export(caseId, lawyerId, "DOCX", orgIds);

    CaseExportModel model = captor.getValue();
    assertThat(model.documents()).hasSize(1);
    assertThat(model.documents().get(0).status()).isEqualTo("Обработан");
    assertThat(model.tasks()).hasSize(1);
    assertThat(model.tasks().get(0).text()).isEqualTo("Подать иск");
    assertThat(model.responses()).hasSize(1);
    assertThat(model.responses().get(0).sources()).containsExactly("Источник 1");
    assertThat(model.drafts()).hasSize(1);
    assertThat(model.drafts().get(0).typeName()).isEqualTo("Жалоба");
  }

  @Test
  void draftTypeLabelFallsBackToRawValueWhenUnknown() {
    Case caseEntity = caseEntity("Дело №1", null);
    when(caseService.requireVisibleCase(caseId, lawyerId, orgIds)).thenReturn(caseEntity);
    when(documentQuery.findByCase(caseId)).thenReturn(List.of());
    when(caseTaskRepository.findByCaseIdOrderByDoneAscCreatedAtAsc(caseId)).thenReturn(List.of());
    when(aiResponseQuery.listByCase(caseId)).thenReturn(List.of());

    CaseDraft draft = new CaseDraft();
    draft.setDraftType("UNKNOWN_TYPE");
    draft.setTitle("Черновик");
    draft.setContent("Текст");
    when(caseDraftRepository.findByCaseIdOrderByCreatedAtDesc(caseId)).thenReturn(List.of(draft));

    ArgumentCaptor<CaseExportModel> captor = ArgumentCaptor.forClass(CaseExportModel.class);
    when(docxWriter.write(captor.capture())).thenReturn(new byte[] {0});

    service.export(caseId, lawyerId, "DOCX", orgIds);

    assertThat(captor.getValue().drafts().get(0).typeName()).isEqualTo("UNKNOWN_TYPE");
  }
}
