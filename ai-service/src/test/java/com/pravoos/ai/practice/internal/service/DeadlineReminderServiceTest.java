package com.pravoos.ai.practice.internal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pravoos.ai.practice.internal.model.entity.CaseDeadlineReminder;
import com.pravoos.ai.practice.internal.repository.jpa.CaseDeadlineReminderRepository;
import com.pravoos.ai.practice.internal.repository.jpa.CaseRepository;
import com.pravoos.ai.practice.internal.repository.jpa.CaseTaskRepository;
import com.pravoos.ai.shared.event.CaseDeadlineKafkaPayload;
import com.pravoos.ai.shared.model.enums.DeadlineType;
import com.pravoos.ai.shared.service.OutboxEventService;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DeadlineReminderServiceTest {

  @Mock private CaseRepository caseRepository;
  @Mock private CaseTaskRepository caseTaskRepository;
  @Mock private CaseDeadlineReminderRepository reminderRepository;
  @Mock private OutboxEventService outboxEventService;

  private DeadlineReminderService service;

  private final UUID caseId = UUID.randomUUID();
  private final UUID lawyerId = UUID.randomUUID();
  private final UUID taskId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    service =
        new DeadlineReminderService(
            caseRepository, caseTaskRepository, reminderRepository, outboxEventService, null);
  }

  private CaseTaskRepository.TaskReminderView taskDueIn(int days) {
    LocalDate dueDate = LocalDate.now().plusDays(days);
    return new CaseTaskRepository.TaskReminderView() {
      @Override
      public UUID getId() {
        return taskId;
      }

      @Override
      public UUID getCaseId() {
        return caseId;
      }

      @Override
      public UUID getLawyerId() {
        return lawyerId;
      }

      @Override
      public String getCaseTitle() {
        return "Дело о банкротстве";
      }

      @Override
      public String getText() {
        return "Подготовить возражение";
      }

      @Override
      public LocalDate getDueDate() {
        return dueDate;
      }
    };
  }

  @Test
  void enqueuesTaskReminder_whenNotAlreadySent() {
    var task = taskDueIn(3);
    when(reminderRepository.existsByCaseIdAndDeadlineTypeAndDeadlineDateAndThresholdDaysAndTaskId(
            caseId, DeadlineType.TASK, task.getDueDate(), 3, taskId))
        .thenReturn(false);

    boolean result = service.enqueueTaskReminder(task, 3);

    assertThat(result).isTrue();
    ArgumentCaptor<CaseDeadlineReminder> reminderCaptor =
        ArgumentCaptor.forClass(CaseDeadlineReminder.class);
    verify(reminderRepository).save(reminderCaptor.capture());
    assertThat(reminderCaptor.getValue().getTaskId()).isEqualTo(taskId);
    assertThat(reminderCaptor.getValue().getDeadlineType()).isEqualTo(DeadlineType.TASK);

    ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
    verify(outboxEventService)
        .enqueue(
            org.mockito.ArgumentMatchers.eq("case.deadline.approaching"),
            org.mockito.ArgumentMatchers.eq(caseId.toString()),
            payloadCaptor.capture());
    CaseDeadlineKafkaPayload payload = (CaseDeadlineKafkaPayload) payloadCaptor.getValue();
    assertThat(payload.caseId()).isEqualTo(caseId);
    assertThat(payload.lawyerId()).isEqualTo(lawyerId);
    assertThat(payload.deadlineTypeName()).isEqualTo("Подготовить возражение");
    assertThat(payload.daysLeft()).isEqualTo(3);
  }

  @Test
  void doesNotEnqueueTaskReminder_whenAlreadySent() {
    var task = taskDueIn(1);
    when(reminderRepository.existsByCaseIdAndDeadlineTypeAndDeadlineDateAndThresholdDaysAndTaskId(
            caseId, DeadlineType.TASK, task.getDueDate(), 1, taskId))
        .thenReturn(true);

    boolean result = service.enqueueTaskReminder(task, 1);

    assertThat(result).isFalse();
    verify(reminderRepository, never()).save(any());
    verify(outboxEventService, never()).enqueue(any(), any(), any());
  }
}
