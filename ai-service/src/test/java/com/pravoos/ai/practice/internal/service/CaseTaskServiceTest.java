package com.pravoos.ai.practice.internal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pravoos.ai.core.api.AiResponseDto;
import com.pravoos.ai.practice.internal.dto.CaseTaskResponse;
import com.pravoos.ai.practice.internal.dto.CreateCaseTaskRequest;
import com.pravoos.ai.practice.internal.dto.UpdateCaseTaskRequest;
import com.pravoos.ai.practice.internal.model.entity.CaseTask;
import com.pravoos.ai.practice.internal.repository.jpa.CaseTaskRepository;
import com.pravoos.ai.shared.exception.CaseTaskNotFoundException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class CaseTaskServiceTest {

  @Mock private CaseTaskRepository caseTaskRepository;
  @Mock private CaseService caseService;
  @Mock private WorkflowService workflowService;

  @InjectMocks private CaseTaskService caseTaskService;

  private CaseTask taskWithId(UUID id, UUID caseId, String text, boolean done) {
    CaseTask task = new CaseTask();
    ReflectionTestUtils.setField(task, "id", id);
    task.setCaseId(caseId);
    task.setText(text);
    task.setDone(done);
    return task;
  }

  @Test
  void createChecksVisibilityAndSavesTrimmedText() {
    UUID caseId = UUID.randomUUID();
    UUID lawyerId = UUID.randomUUID();
    List<UUID> orgIds = List.of(UUID.randomUUID());
    when(caseTaskRepository.save(any(CaseTask.class)))
        .thenAnswer(
            invocation -> {
              CaseTask saved = invocation.getArgument(0);
              ReflectionTestUtils.setField(saved, "id", UUID.randomUUID());
              return saved;
            });

    CaseTaskResponse response =
        caseTaskService.create(
            caseId, new CreateCaseTaskRequest("  Подать иск  ", null), lawyerId, orgIds);

    verify(caseService).requireVisibleCase(caseId, lawyerId, orgIds);
    assertThat(response.text()).isEqualTo("Подать иск");
    assertThat(response.caseId()).isEqualTo(caseId);
  }

  @Test
  void findByCaseChecksVisibilityAndMapsTasks() {
    UUID caseId = UUID.randomUUID();
    UUID lawyerId = UUID.randomUUID();
    List<UUID> orgIds = List.of();
    CaseTask task = taskWithId(UUID.randomUUID(), caseId, "Задача", false);
    when(caseTaskRepository.findByCaseIdOrderByDoneAscCreatedAtAsc(caseId))
        .thenReturn(List.of(task));

    List<CaseTaskResponse> result = caseTaskService.findByCase(caseId, lawyerId, orgIds);

    verify(caseService).requireVisibleCase(caseId, lawyerId, orgIds);
    assertThat(result).hasSize(1);
    assertThat(result.get(0).text()).isEqualTo("Задача");
  }

  @Test
  void updateModifiesTaskBelongingToCase() {
    UUID caseId = UUID.randomUUID();
    UUID lawyerId = UUID.randomUUID();
    UUID taskId = UUID.randomUUID();
    List<UUID> orgIds = List.of();
    CaseTask task = taskWithId(taskId, caseId, "Старый текст", false);
    when(caseTaskRepository.findById(taskId)).thenReturn(Optional.of(task));

    CaseTaskResponse response =
        caseTaskService.update(
            caseId,
            taskId,
            new UpdateCaseTaskRequest(" Новый текст ", LocalDate.now(), true),
            lawyerId,
            orgIds);

    verify(caseService).requireVisibleCase(caseId, lawyerId, orgIds);
    assertThat(response.text()).isEqualTo("Новый текст");
    assertThat(response.done()).isTrue();
  }

  @Test
  void updateThrowsWhenTaskBelongsToDifferentCase() {
    UUID caseId = UUID.randomUUID();
    UUID otherCaseId = UUID.randomUUID();
    UUID lawyerId = UUID.randomUUID();
    UUID taskId = UUID.randomUUID();
    CaseTask task = taskWithId(taskId, otherCaseId, "Текст", false);
    when(caseTaskRepository.findById(taskId)).thenReturn(Optional.of(task));

    assertThatThrownBy(
            () ->
                caseTaskService.update(
                    caseId,
                    taskId,
                    new UpdateCaseTaskRequest("Текст", null, true),
                    lawyerId,
                    List.of()))
        .isInstanceOf(CaseTaskNotFoundException.class);
  }

  @Test
  void updateThrowsWhenTaskNotFound() {
    UUID caseId = UUID.randomUUID();
    UUID lawyerId = UUID.randomUUID();
    UUID taskId = UUID.randomUUID();
    when(caseTaskRepository.findById(taskId)).thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                caseTaskService.update(
                    caseId,
                    taskId,
                    new UpdateCaseTaskRequest("Текст", null, true),
                    lawyerId,
                    List.of()))
        .isInstanceOf(CaseTaskNotFoundException.class);
  }

  @Test
  void deleteRemovesTaskBelongingToCase() {
    UUID caseId = UUID.randomUUID();
    UUID lawyerId = UUID.randomUUID();
    UUID taskId = UUID.randomUUID();
    CaseTask task = taskWithId(taskId, caseId, "Текст", false);
    when(caseTaskRepository.findById(taskId)).thenReturn(Optional.of(task));

    caseTaskService.delete(caseId, taskId, lawyerId, List.of());

    verify(caseService).requireVisibleCase(caseId, lawyerId, List.of());
    verify(caseTaskRepository).delete(task);
  }

  @Test
  void deleteThrowsWhenTaskBelongsToDifferentCaseAndDoesNotDelete() {
    UUID caseId = UUID.randomUUID();
    UUID otherCaseId = UUID.randomUUID();
    UUID lawyerId = UUID.randomUUID();
    UUID taskId = UUID.randomUUID();
    CaseTask task = taskWithId(taskId, otherCaseId, "Текст", false);
    when(caseTaskRepository.findById(taskId)).thenReturn(Optional.of(task));

    assertThatThrownBy(() -> caseTaskService.delete(caseId, taskId, lawyerId, List.of()))
        .isInstanceOf(CaseTaskNotFoundException.class);
    verify(caseTaskRepository, never()).delete(any());
  }

  @Test
  void createFromChecklistReturnsEmptyWhenNoTasksExtracted() {
    UUID caseId = UUID.randomUUID();

    List<CaseTaskResponse> result =
        caseTaskService.createFromChecklist(caseId, "не таблица", UUID.randomUUID());

    assertThat(result).isEmpty();
    verify(caseTaskRepository, never()).saveAll(any());
  }

  @Test
  void createFromChecklistSkipsDuplicatesOfExistingOpenTasks() {
    UUID caseId = UUID.randomUUID();
    UUID lawyerId = UUID.randomUUID();
    String markdown =
        "| Документ | Статус | Примечание |\n"
            + "|---|---|---|\n"
            + "| Заявление | отсутствует | нужно приложить |\n"
            + "| Реестр кредиторов | отсутствует | нужно приложить |\n";

    CaseTask existingOpen =
        taskWithId(
            UUID.randomUUID(), caseId, "Подготовить документ: Заявление (нужно приложить)", false);
    when(caseTaskRepository.findByCaseIdOrderByDoneAscCreatedAtAsc(caseId))
        .thenReturn(List.of(existingOpen));
    when(caseTaskRepository.saveAll(anyList()))
        .thenAnswer(
            invocation -> {
              List<CaseTask> tasks = invocation.getArgument(0);
              tasks.forEach(task -> ReflectionTestUtils.setField(task, "id", UUID.randomUUID()));
              return tasks;
            });

    List<CaseTaskResponse> result = caseTaskService.createFromChecklist(caseId, markdown, lawyerId);

    ArgumentCaptor<List<CaseTask>> captor = ArgumentCaptor.forClass(List.class);
    verify(caseTaskRepository).saveAll(captor.capture());
    assertThat(captor.getValue()).hasSize(1);
    assertThat(captor.getValue().get(0).getText())
        .isEqualTo("Подготовить документ: Реестр кредиторов (нужно приложить)");
    assertThat(result).hasSize(1);
  }

  @Test
  void generateFromChecklistChecksVisibilityRunsWorkflowAndCreatesTasks() {
    UUID caseId = UUID.randomUUID();
    UUID lawyerId = UUID.randomUUID();
    List<UUID> orgIds = List.of();
    String markdown = "| Документ | Статус |\n|---|---|\n| Заявление | отсутствует |\n";
    AiResponseDto checklistResponse =
        new AiResponseDto(
            UUID.randomUUID(),
            caseId,
            "DOCUMENT_CHECKLIST",
            "Чеклист",
            "q",
            markdown,
            List.of(),
            null,
            null,
            null,
            List.of());
    when(workflowService.run(
            eq(caseId), eq("DOCUMENT_CHECKLIST"), eq(null), eq(lawyerId), eq(orgIds)))
        .thenReturn(checklistResponse);
    when(caseTaskRepository.findByCaseIdOrderByDoneAscCreatedAtAsc(caseId)).thenReturn(List.of());
    when(caseTaskRepository.saveAll(anyList()))
        .thenAnswer(
            invocation -> {
              List<CaseTask> tasks = invocation.getArgument(0);
              tasks.forEach(task -> ReflectionTestUtils.setField(task, "id", UUID.randomUUID()));
              return tasks;
            });

    List<CaseTaskResponse> result = caseTaskService.generateFromChecklist(caseId, lawyerId, orgIds);

    verify(caseService).requireVisibleCase(caseId, lawyerId, orgIds);
    assertThat(result).hasSize(1);
    assertThat(result.get(0).text()).isEqualTo("Подготовить документ: Заявление");
  }
}
