package com.pravoos.ai.practice.internal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.pravoos.ai.practice.internal.dto.SaveWorkflowDefinitionRequest;
import com.pravoos.ai.practice.internal.dto.WorkflowDefinitionDto;
import com.pravoos.ai.practice.internal.dto.WorkflowStepDto;
import com.pravoos.ai.practice.internal.model.entity.WorkflowDefinition;
import com.pravoos.ai.practice.internal.repository.jpa.WorkflowDefinitionRepository;
import com.pravoos.ai.recyclebin.api.DeletionActor;
import com.pravoos.ai.recyclebin.api.DeletionRole;
import com.pravoos.ai.recyclebin.api.RecycleBin;
import com.pravoos.ai.recyclebin.api.RecycleBinEntityType;
import com.pravoos.ai.shared.exception.InvalidWorkflowDefinitionException;
import com.pravoos.ai.shared.exception.WorkflowDefinitionNotFoundException;
import com.pravoos.ai.shared.model.enums.WorkflowCategory;
import com.pravoos.ai.shared.model.enums.WorkflowStepType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WorkflowDefinitionServiceTest {

  @Mock private WorkflowDefinitionRepository repository;
  @Mock private RecycleBin recycleBin;

  private final UUID lawyerId = UUID.randomUUID();

  private WorkflowDefinitionService service() {
    return new WorkflowDefinitionService(
        repository, recycleBin, new WorkflowDefinitionCatalogCache(), true);
  }

  private DeletionActor actor(UUID userId) {
    return new DeletionActor(userId, DeletionRole.LAWYER, null, List.of());
  }

  private SaveWorkflowDefinitionRequest request(WorkflowStepDto... steps) {
    return new SaveWorkflowDefinitionRequest(
        "Процесс", "описание", WorkflowCategory.CUSTOM, List.of(steps));
  }

  @Test
  void createPersistsValidDefinition() {
    when(repository.save(any(WorkflowDefinition.class))).thenAnswer(inv -> inv.getArgument(0));

    WorkflowDefinitionDto dto =
        service()
            .create(
                request(
                    new WorkflowStepDto(
                        WorkflowStepType.AI_ANALYSIS, "Анализ", "проанализируй", null, null, null)),
                lawyerId);

    assertThat(dto.editable()).isTrue();
    assertThat(dto.steps()).hasSize(1);
    assertThat(dto.steps().get(0).order()).isZero();
  }

  @Test
  void createRejectsAiStepWithoutInstruction() {
    assertThatThrownBy(
            () ->
                service()
                    .create(
                        request(
                            new WorkflowStepDto(
                                WorkflowStepType.AI_ANALYSIS, "Анализ", "  ", null, null, null)),
                        lawyerId))
        .isInstanceOf(InvalidWorkflowDefinitionException.class);
  }

  @Test
  void createRejectsDraftStepWithUnknownType() {
    assertThatThrownBy(
            () ->
                service()
                    .create(
                        request(
                            new WorkflowStepDto(
                                WorkflowStepType.GENERATE_DRAFT,
                                "Черновик",
                                null,
                                "UNKNOWN",
                                null,
                                null)),
                        lawyerId))
        .isInstanceOf(InvalidWorkflowDefinitionException.class);
  }

  @Test
  void updateRejectsSystemDefinition() {
    UUID id = UUID.randomUUID();
    WorkflowDefinition system = new WorkflowDefinition();
    system.setSystem(true);
    system.setCategory(WorkflowCategory.BANKRUPTCY);
    when(repository.findById(id)).thenReturn(Optional.of(system));

    assertThatThrownBy(
            () ->
                service()
                    .update(
                        id,
                        request(
                            new WorkflowStepDto(
                                WorkflowStepType.AI_ANALYSIS, "Анализ", "x", null, null, null)),
                        lawyerId,
                        List.of()))
        .isInstanceOf(InvalidWorkflowDefinitionException.class);
  }

  @Test
  void requireVisibleHidesForeignPersonalDefinition() {
    UUID id = UUID.randomUUID();
    WorkflowDefinition foreign = new WorkflowDefinition();
    foreign.setCreatedBy(UUID.randomUUID());
    foreign.setCategory(WorkflowCategory.CUSTOM);
    when(repository.findById(id)).thenReturn(Optional.of(foreign));

    assertThatThrownBy(() -> service().requireVisible(id, lawyerId, List.of()))
        .isInstanceOf(WorkflowDefinitionNotFoundException.class);
  }

  @Test
  void deleteMovesOwnedDefinitionToRecycleBin() {
    UUID id = UUID.randomUUID();
    WorkflowDefinition owned = new WorkflowDefinition();
    owned.setCreatedBy(lawyerId);
    owned.setCategory(WorkflowCategory.CUSTOM);
    when(repository.findById(id)).thenReturn(Optional.of(owned));
    DeletionActor actor = actor(lawyerId);

    service().delete(id, actor);

    verify(recycleBin).moveToBin(RecycleBinEntityType.WORKFLOW_DEFINITION, id.toString(), actor);
  }

  @Test
  void deleteRejectsSystemDefinition() {
    UUID id = UUID.randomUUID();
    WorkflowDefinition system = new WorkflowDefinition();
    system.setSystem(true);
    system.setCategory(WorkflowCategory.CUSTOM);
    when(repository.findById(id)).thenReturn(Optional.of(system));

    assertThatThrownBy(() -> service().delete(id, actor(lawyerId)))
        .isInstanceOf(InvalidWorkflowDefinitionException.class);
    verifyNoInteractions(recycleBin);
  }

  @Test
  void listVisibleCacheDoesNotLeakBetweenOrganizations() {
    UUID orgA = UUID.randomUUID();
    UUID orgB = UUID.randomUUID();
    WorkflowDefinition sharedInOrgA = new WorkflowDefinition();
    sharedInOrgA.setCreatedBy(UUID.randomUUID());
    sharedInOrgA.setOrgId(orgA);
    sharedInOrgA.setCategory(WorkflowCategory.CUSTOM);
    sharedInOrgA.setName("Процесс организации A");
    WorkflowDefinition sharedInOrgB = new WorkflowDefinition();
    sharedInOrgB.setCreatedBy(UUID.randomUUID());
    sharedInOrgB.setOrgId(orgB);
    sharedInOrgB.setCategory(WorkflowCategory.CUSTOM);
    sharedInOrgB.setName("Процесс организации B");
    when(repository.findVisible(lawyerId, List.of(orgA))).thenReturn(List.of(sharedInOrgA));
    when(repository.findVisible(lawyerId, List.of(orgB))).thenReturn(List.of(sharedInOrgB));
    WorkflowDefinitionService service = service();

    List<WorkflowDefinitionDto> forOrgA = service.listVisible(lawyerId, List.of(orgA));
    List<WorkflowDefinitionDto> forOrgB = service.listVisible(lawyerId, List.of(orgB));

    assertThat(forOrgA)
        .extracting(WorkflowDefinitionDto::name)
        .containsExactly("Процесс организации A");
    assertThat(forOrgB)
        .extracting(WorkflowDefinitionDto::name)
        .containsExactly("Процесс организации B");
  }

  @Test
  void createEvictsCacheSoNextListIsFresh() {
    List<UUID> noOrgSentinel = List.of(new UUID(0L, 0L));
    when(repository.findVisible(lawyerId, noOrgSentinel)).thenReturn(List.of());
    when(repository.save(any(WorkflowDefinition.class))).thenAnswer(inv -> inv.getArgument(0));
    WorkflowDefinitionService service = service();

    service.listVisible(lawyerId, List.of());
    service.create(
        request(new WorkflowStepDto(WorkflowStepType.AI_ANALYSIS, "Анализ", "x", null, null, null)),
        lawyerId);
    service.listVisible(lawyerId, List.of());

    verify(repository, times(2)).findVisible(lawyerId, noOrgSentinel);
  }
}
