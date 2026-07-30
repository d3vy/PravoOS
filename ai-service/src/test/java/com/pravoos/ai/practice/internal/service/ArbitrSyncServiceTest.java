package com.pravoos.ai.practice.internal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pravoos.ai.practice.internal.model.entity.Case;
import com.pravoos.ai.practice.internal.model.entity.CaseTask;
import com.pravoos.ai.practice.internal.repository.jpa.CaseHearingEventRepository;
import com.pravoos.ai.practice.internal.repository.jpa.CasePartyRepository;
import com.pravoos.ai.practice.internal.repository.jpa.CaseRepository;
import com.pravoos.ai.practice.internal.repository.jpa.CaseTaskRepository;
import com.pravoos.ai.shared.arbitr.ArbitrCaseData;
import com.pravoos.ai.shared.arbitr.ArbitrCaseProvider;
import com.pravoos.ai.shared.service.OutboxEventService;
import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ArbitrSyncServiceTest {

  @Mock private ArbitrCaseProvider arbitrCaseProvider;
  @Mock private CaseRepository caseRepository;
  @Mock private CaseHearingEventRepository hearingEventRepository;
  @Mock private CasePartyRepository casePartyRepository;
  @Mock private CaseTaskRepository caseTaskRepository;
  @Mock private OutboxEventService outboxEventService;

  private ArbitrSyncService service;

  private final UUID caseId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    service =
        new ArbitrSyncService(
            arbitrCaseProvider,
            caseRepository,
            hearingEventRepository,
            casePartyRepository,
            caseTaskRepository,
            outboxEventService,
            null);
  }

  private Case caseEntity(LocalDate currentHearingDate) {
    Case caseEntity = new Case();
    setId(caseEntity, caseId);
    caseEntity.setTitle("Дело о банкротстве");
    caseEntity.setNextHearingDate(currentHearingDate);
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

  private ArbitrCaseData dataWithHearingDate(LocalDate hearingDate) {
    return new ArbitrCaseData("А40-1/2026", null, hearingDate, null, List.of(), List.of());
  }

  @Test
  void createsPreparationTask_whenHearingDateMovesToTheFuture() {
    Case caseEntity = caseEntity(null);
    when(caseRepository.findById(caseId)).thenReturn(java.util.Optional.of(caseEntity));
    when(caseTaskRepository.findByCaseIdOrderByDoneAscCreatedAtAsc(caseId)).thenReturn(List.of());
    LocalDate hearingDate = LocalDate.now().plusDays(10);

    service.persistSyncedCase(caseId, dataWithHearingDate(hearingDate));

    ArgumentCaptor<CaseTask> captor = ArgumentCaptor.forClass(CaseTask.class);
    verify(caseTaskRepository).save(captor.capture());
    CaseTask created = captor.getValue();
    assertThat(created.getCaseId()).isEqualTo(caseId);
    assertThat(created.getDueDate()).isEqualTo(hearingDate.minusDays(3));
    assertThat(created.getText())
        .contains(hearingDate.format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy")));
  }

  @Test
  void doesNotCreatePreparationTask_whenHearingDateUnchanged() {
    LocalDate hearingDate = LocalDate.now().plusDays(10);
    Case caseEntity = caseEntity(hearingDate);
    when(caseRepository.findById(caseId)).thenReturn(java.util.Optional.of(caseEntity));

    service.persistSyncedCase(caseId, dataWithHearingDate(hearingDate));

    verify(caseTaskRepository, never()).save(any());
  }

  @Test
  void doesNotCreatePreparationTask_whenDuplicateAlreadyOpen() {
    Case caseEntity = caseEntity(null);
    when(caseRepository.findById(caseId)).thenReturn(java.util.Optional.of(caseEntity));
    LocalDate hearingDate = LocalDate.now().plusDays(10);
    CaseTask existing = new CaseTask();
    existing.setCaseId(caseId);
    existing.setText(
        "Подготовиться к заседанию "
            + hearingDate.format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy")));
    when(caseTaskRepository.findByCaseIdOrderByDoneAscCreatedAtAsc(caseId))
        .thenReturn(List.of(existing));

    service.persistSyncedCase(caseId, dataWithHearingDate(hearingDate));

    verify(caseTaskRepository, never()).save(any());
  }

  @Test
  void doesNotCreatePreparationTask_whenHearingDateInThePast() {
    Case caseEntity = caseEntity(null);
    when(caseRepository.findById(caseId)).thenReturn(java.util.Optional.of(caseEntity));
    LocalDate hearingDate = LocalDate.now().minusDays(2);

    service.persistSyncedCase(caseId, dataWithHearingDate(hearingDate));

    verify(caseTaskRepository, never()).save(any());
  }
}
