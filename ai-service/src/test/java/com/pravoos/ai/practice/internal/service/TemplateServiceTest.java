package com.pravoos.ai.practice.internal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pravoos.ai.practice.internal.dto.CaseDraftDto;
import com.pravoos.ai.practice.internal.dto.CreateTemplateRequest;
import com.pravoos.ai.practice.internal.dto.TemplateResponse;
import com.pravoos.ai.practice.internal.dto.UpdateTemplateRequest;
import com.pravoos.ai.practice.internal.model.entity.Case;
import com.pravoos.ai.practice.internal.model.entity.CaseDraft;
import com.pravoos.ai.practice.internal.model.entity.Client;
import com.pravoos.ai.practice.internal.model.entity.DocumentTemplate;
import com.pravoos.ai.practice.internal.repository.jpa.CaseDraftRepository;
import com.pravoos.ai.practice.internal.repository.jpa.ClientRepository;
import com.pravoos.ai.practice.internal.repository.jpa.DocumentTemplateRepository;
import com.pravoos.ai.recyclebin.api.DeletionActor;
import com.pravoos.ai.recyclebin.api.DeletionRole;
import com.pravoos.ai.recyclebin.api.RecycleBin;
import com.pravoos.ai.recyclebin.api.RecycleBinEntityType;
import com.pravoos.ai.shared.exception.TemplateNotFoundException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class TemplateServiceTest {

  @Mock private DocumentTemplateRepository templateRepository;
  @Mock private CaseDraftRepository caseDraftRepository;
  @Mock private ClientRepository clientRepository;
  @Mock private CaseService caseService;
  @Mock private RecycleBin recycleBin;

  private TemplateService templateService;

  private final UUID lawyerId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    templateService =
        new TemplateService(
            templateRepository,
            caseDraftRepository,
            clientRepository,
            caseService,
            recycleBin,
            new TemplateCatalogCache(),
            true);
  }

  private DocumentTemplate templateWithId(UUID id, UUID owner, String name, String content) {
    DocumentTemplate template = new DocumentTemplate();
    ReflectionTestUtils.setField(template, "id", id);
    template.setLawyerId(owner);
    template.setName(name);
    template.setContent(content);
    return template;
  }

  @Test
  void createTrimsNameAndSavesTemplate() {
    when(templateRepository.save(any(DocumentTemplate.class)))
        .thenAnswer(
            invocation -> {
              DocumentTemplate saved = invocation.getArgument(0);
              ReflectionTestUtils.setField(saved, "id", UUID.randomUUID());
              return saved;
            });

    TemplateResponse response =
        templateService.create(
            new CreateTemplateRequest("  Иск  ", "Текст {{case_title}}"), lawyerId);

    ArgumentCaptor<DocumentTemplate> captor = ArgumentCaptor.forClass(DocumentTemplate.class);
    verify(templateRepository).save(captor.capture());
    assertThat(captor.getValue().getName()).isEqualTo("Иск");
    assertThat(captor.getValue().getLawyerId()).isEqualTo(lawyerId);
    assertThat(response.name()).isEqualTo("Иск");
    assertThat(response.content()).isEqualTo("Текст {{case_title}}");
  }

  @Test
  void findByLawyerMapsAllTemplates() {
    UUID id1 = UUID.randomUUID();
    UUID id2 = UUID.randomUUID();
    when(templateRepository.findByLawyerIdOrderByCreatedAtDesc(lawyerId))
        .thenReturn(
            List.of(
                templateWithId(id1, lawyerId, "Шаблон 1", "A"),
                templateWithId(id2, lawyerId, "Шаблон 2", "B")));

    List<TemplateResponse> result = templateService.findByLawyer(lawyerId);

    assertThat(result).extracting(TemplateResponse::id).containsExactly(id1, id2);
  }

  @Test
  void getReturnsTemplateWhenOwnedByLawyer() {
    UUID templateId = UUID.randomUUID();
    when(templateRepository.findByIdAndLawyerId(templateId, lawyerId))
        .thenReturn(Optional.of(templateWithId(templateId, lawyerId, "Шаблон", "Текст")));

    TemplateResponse response = templateService.get(templateId, lawyerId);

    assertThat(response.id()).isEqualTo(templateId);
    assertThat(response.name()).isEqualTo("Шаблон");
  }

  @Test
  void getThrowsWhenTemplateNotFoundOrNotOwned() {
    UUID templateId = UUID.randomUUID();
    when(templateRepository.findByIdAndLawyerId(templateId, lawyerId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> templateService.get(templateId, lawyerId))
        .isInstanceOf(TemplateNotFoundException.class);
  }

  @Test
  void updateChangesNameAndContentInPlace() {
    UUID templateId = UUID.randomUUID();
    DocumentTemplate existing = templateWithId(templateId, lawyerId, "Старое имя", "Старый текст");
    when(templateRepository.findByIdAndLawyerId(templateId, lawyerId))
        .thenReturn(Optional.of(existing));

    TemplateResponse response =
        templateService.update(
            templateId, new UpdateTemplateRequest("  Новое имя  ", "Новый текст"), lawyerId);

    assertThat(response.name()).isEqualTo("Новое имя");
    assertThat(response.content()).isEqualTo("Новый текст");
    assertThat(existing.getName()).isEqualTo("Новое имя");
    assertThat(existing.getContent()).isEqualTo("Новый текст");
  }

  @Test
  void updateThrowsWhenTemplateNotOwned() {
    UUID templateId = UUID.randomUUID();
    when(templateRepository.findByIdAndLawyerId(templateId, lawyerId)).thenReturn(Optional.empty());

    assertThatThrownBy(
            () -> templateService.update(templateId, new UpdateTemplateRequest("X", "Y"), lawyerId))
        .isInstanceOf(TemplateNotFoundException.class);
  }

  @Test
  void deleteMovesOwnedTemplateToRecycleBin() {
    UUID templateId = UUID.randomUUID();
    DocumentTemplate existing = templateWithId(templateId, lawyerId, "Шаблон", "Текст");
    when(templateRepository.findByIdAndLawyerId(templateId, lawyerId))
        .thenReturn(Optional.of(existing));
    DeletionActor actor = actor();

    templateService.delete(templateId, actor);

    verify(recycleBin).moveToBin(RecycleBinEntityType.TEMPLATE, templateId.toString(), actor);
  }

  @Test
  void deleteThrowsWhenTemplateNotOwnedAndDoesNotTouchRecycleBin() {
    UUID templateId = UUID.randomUUID();
    when(templateRepository.findByIdAndLawyerId(templateId, lawyerId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> templateService.delete(templateId, actor()))
        .isInstanceOf(TemplateNotFoundException.class);
    verify(recycleBin, never()).moveToBin(any(), any(), any());
  }

  private DeletionActor actor() {
    return new DeletionActor(lawyerId, DeletionRole.LAWYER, null, List.of());
  }

  @Test
  void applyToCaseResolvesPlaceholdersAndSavesDraft() {
    UUID caseId = UUID.randomUUID();
    UUID templateId = UUID.randomUUID();
    UUID clientId = UUID.randomUUID();
    List<UUID> orgIds = List.of();

    Case caseEntity = new Case();
    ReflectionTestUtils.setField(caseEntity, "id", caseId);
    caseEntity.setLawyerId(lawyerId);
    caseEntity.setClientId(clientId);
    caseEntity.setTitle("Дело о взыскании");
    when(caseService.requireVisibleCase(caseId, lawyerId, orgIds)).thenReturn(caseEntity);

    DocumentTemplate template =
        templateWithId(
            templateId, lawyerId, "Претензия", "Дело: {{case_title}}, клиент: {{client_name}}");
    when(templateRepository.findByIdAndLawyerId(templateId, lawyerId))
        .thenReturn(Optional.of(template));

    Client client = new Client();
    ReflectionTestUtils.setField(client, "id", clientId);
    client.setLawyerId(lawyerId);
    client.setName("Иванов И.И.");
    when(clientRepository.findById(clientId)).thenReturn(Optional.of(client));

    when(caseDraftRepository.save(any(CaseDraft.class)))
        .thenAnswer(
            invocation -> {
              CaseDraft draft = invocation.getArgument(0);
              ReflectionTestUtils.setField(draft, "id", UUID.randomUUID());
              return draft;
            });

    CaseDraftDto result = templateService.applyToCase(caseId, templateId, lawyerId, orgIds);

    assertThat(result.caseId()).isEqualTo(caseId);
    assertThat(result.draftType()).isEqualTo("TEMPLATE");
    assertThat(result.title()).isEqualTo("Претензия");
    assertThat(result.content()).isEqualTo("Дело: Дело о взыскании, клиент: Иванов И.И.");

    ArgumentCaptor<CaseDraft> captor = ArgumentCaptor.forClass(CaseDraft.class);
    verify(caseDraftRepository).save(captor.capture());
    assertThat(captor.getValue().getLawyerId()).isEqualTo(lawyerId);
  }

  @Test
  void applyToCaseResolvesEmptyClientPlaceholdersWhenClientIdIsNull() {
    UUID caseId = UUID.randomUUID();
    UUID templateId = UUID.randomUUID();
    List<UUID> orgIds = List.of();

    Case caseEntity = new Case();
    ReflectionTestUtils.setField(caseEntity, "id", caseId);
    caseEntity.setLawyerId(lawyerId);
    caseEntity.setClientId(null);
    caseEntity.setTitle("Дело без клиента");
    when(caseService.requireVisibleCase(caseId, lawyerId, orgIds)).thenReturn(caseEntity);

    DocumentTemplate template =
        templateWithId(templateId, lawyerId, "Шаблон", "Клиент: [{{client_name}}]");
    when(templateRepository.findByIdAndLawyerId(templateId, lawyerId))
        .thenReturn(Optional.of(template));
    when(caseDraftRepository.save(any(CaseDraft.class)))
        .thenAnswer(
            invocation -> {
              CaseDraft draft = invocation.getArgument(0);
              ReflectionTestUtils.setField(draft, "id", UUID.randomUUID());
              return draft;
            });

    CaseDraftDto result = templateService.applyToCase(caseId, templateId, lawyerId, orgIds);

    assertThat(result.content()).isEqualTo("Клиент: []");
    verify(clientRepository, never()).findById(any());
  }

  @Test
  void applyToCaseResolvesEmptyClientPlaceholdersWhenClientBelongsToAnotherLawyer() {
    UUID caseId = UUID.randomUUID();
    UUID templateId = UUID.randomUUID();
    UUID clientId = UUID.randomUUID();
    List<UUID> orgIds = List.of();

    Case caseEntity = new Case();
    ReflectionTestUtils.setField(caseEntity, "id", caseId);
    caseEntity.setLawyerId(lawyerId);
    caseEntity.setClientId(clientId);
    caseEntity.setTitle("Дело");
    when(caseService.requireVisibleCase(caseId, lawyerId, orgIds)).thenReturn(caseEntity);

    DocumentTemplate template =
        templateWithId(templateId, lawyerId, "Шаблон", "Клиент: [{{client_name}}]");
    when(templateRepository.findByIdAndLawyerId(templateId, lawyerId))
        .thenReturn(Optional.of(template));

    Client foreignClient = new Client();
    ReflectionTestUtils.setField(foreignClient, "id", clientId);
    foreignClient.setLawyerId(UUID.randomUUID());
    foreignClient.setName("Чужой клиент");
    when(clientRepository.findById(clientId)).thenReturn(Optional.of(foreignClient));

    when(caseDraftRepository.save(any(CaseDraft.class)))
        .thenAnswer(
            invocation -> {
              CaseDraft draft = invocation.getArgument(0);
              ReflectionTestUtils.setField(draft, "id", UUID.randomUUID());
              return draft;
            });

    CaseDraftDto result = templateService.applyToCase(caseId, templateId, lawyerId, orgIds);

    assertThat(result.content()).isEqualTo("Клиент: []");
  }

  @Test
  void applyToCaseThrowsWhenTemplateNotOwned() {
    UUID caseId = UUID.randomUUID();
    UUID templateId = UUID.randomUUID();
    List<UUID> orgIds = List.of();
    Case caseEntity = new Case();
    ReflectionTestUtils.setField(caseEntity, "id", caseId);
    when(caseService.requireVisibleCase(caseId, lawyerId, orgIds)).thenReturn(caseEntity);
    when(templateRepository.findByIdAndLawyerId(templateId, lawyerId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> templateService.applyToCase(caseId, templateId, lawyerId, orgIds))
        .isInstanceOf(TemplateNotFoundException.class);
    verify(caseDraftRepository, never()).save(any());
  }
}
