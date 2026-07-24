package com.pravoos.ai.practice.internal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.pravoos.ai.practice.internal.dto.CaseTimeSummary;
import com.pravoos.ai.practice.internal.dto.CreateTimeEntryRequest;
import com.pravoos.ai.practice.internal.dto.StartTimerRequest;
import com.pravoos.ai.practice.internal.dto.TimeEntryResponse;
import com.pravoos.ai.practice.internal.dto.UpdateTimeEntryRequest;
import com.pravoos.ai.practice.internal.model.entity.Case;
import com.pravoos.ai.practice.internal.model.entity.TimeEntry;
import com.pravoos.ai.practice.internal.repository.jpa.TimeEntryRepository;
import com.pravoos.ai.shared.exception.TimeEntryLockedException;
import com.pravoos.ai.shared.exception.TimeEntryNotFoundException;
import com.pravoos.ai.shared.exception.TimerAlreadyRunningException;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TimeEntryServiceTest {

  @Mock private TimeEntryRepository timeEntryRepository;
  @Mock private CaseService caseService;

  private TimeEntryService service;

  private final UUID caseId = UUID.randomUUID();
  private final UUID clientId = UUID.randomUUID();
  private final UUID lawyerId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    service = new TimeEntryService(timeEntryRepository, caseService);
    lenient()
        .when(timeEntryRepository.save(any(TimeEntry.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    lenient()
        .when(timeEntryRepository.saveAndFlush(any(TimeEntry.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
  }

  @Test
  void create_inheritsClientFromCaseAndComputesAmount() {
    when(caseService.requireVisibleCase(caseId, lawyerId, List.of())).thenReturn(caseEntity());

    CreateTimeEntryRequest request =
        new CreateTimeEntryRequest(
            "  Изучение материалов  ", LocalDate.of(2026, 7, 15), 90, new BigDecimal("4000"), true);
    TimeEntryResponse response = service.create(caseId, request, lawyerId, List.of());

    assertThat(response.description()).isEqualTo("Изучение материалов");
    assertThat(response.minutes()).isEqualTo(90);
    assertThat(response.amount()).isEqualByComparingTo("6000.00");
    assertThat(response.billable()).isTrue();
  }

  @Test
  void summary_countsBillableAndUninvoicedExcludingRunning() {
    when(caseService.requireVisibleCase(caseId, lawyerId, List.of())).thenReturn(caseEntity());
    TimeEntry billable = entry(60, new BigDecimal("2000"), true, null, false);
    TimeEntry invoiced = entry(30, new BigDecimal("2000"), true, UUID.randomUUID(), false);
    TimeEntry nonBillable = entry(45, new BigDecimal("2000"), false, null, false);
    TimeEntry running = entry(0, new BigDecimal("2000"), true, null, true);
    when(timeEntryRepository.findByCaseIdOrderByActivityDateDescCreatedAtDesc(caseId))
        .thenReturn(List.of(billable, invoiced, nonBillable, running));

    CaseTimeSummary summary = service.summary(caseId, lawyerId, List.of());

    assertThat(summary.totalMinutes()).isEqualTo(135);
    assertThat(summary.billableMinutes()).isEqualTo(90);
    assertThat(summary.uninvoicedBillableMinutes()).isEqualTo(60);
    assertThat(summary.billableAmount()).isEqualByComparingTo("3000.00");
    assertThat(summary.uninvoicedBillableAmount()).isEqualByComparingTo("2000.00");
  }

  @Test
  void update_rejectsInvoicedEntry() {
    when(caseService.requireVisibleCase(caseId, lawyerId, List.of())).thenReturn(caseEntity());
    TimeEntry invoiced = entry(60, new BigDecimal("2000"), true, UUID.randomUUID(), false);
    when(timeEntryRepository.findById(invoiced.getId())).thenReturn(Optional.of(invoiced));

    UpdateTimeEntryRequest request =
        new UpdateTimeEntryRequest(
            "x", LocalDate.of(2026, 7, 15), 30, new BigDecimal("1000"), true);
    assertThatThrownBy(() -> service.update(caseId, invoiced.getId(), request, lawyerId, List.of()))
        .isInstanceOf(TimeEntryLockedException.class);
  }

  @Test
  void update_rejectsEntryFromOtherCase() {
    when(caseService.requireVisibleCase(caseId, lawyerId, List.of())).thenReturn(caseEntity());
    TimeEntry foreign = entry(60, new BigDecimal("2000"), true, null, false);
    setField(foreign, "caseId", UUID.randomUUID());
    when(timeEntryRepository.findById(foreign.getId())).thenReturn(Optional.of(foreign));

    UpdateTimeEntryRequest request =
        new UpdateTimeEntryRequest(
            "x", LocalDate.of(2026, 7, 15), 30, new BigDecimal("1000"), true);
    assertThatThrownBy(() -> service.update(caseId, foreign.getId(), request, lawyerId, List.of()))
        .isInstanceOf(TimeEntryNotFoundException.class);
  }

  @Test
  void delete_rejectsInvoicedEntry() {
    when(caseService.requireVisibleCase(caseId, lawyerId, List.of())).thenReturn(caseEntity());
    TimeEntry invoiced = entry(60, new BigDecimal("2000"), true, UUID.randomUUID(), false);
    when(timeEntryRepository.findById(invoiced.getId())).thenReturn(Optional.of(invoiced));

    assertThatThrownBy(() -> service.delete(caseId, invoiced.getId(), lawyerId, List.of()))
        .isInstanceOf(TimeEntryLockedException.class);
    verify(timeEntryRepository, never()).delete(any());
  }

  @Test
  void startTimer_rejectsWhenAnotherTimerRuns() {
    when(caseService.requireVisibleCase(caseId, lawyerId, List.of())).thenReturn(caseEntity());
    when(timeEntryRepository.findByLawyerIdAndRunningTrue(lawyerId))
        .thenReturn(Optional.of(entry(0, BigDecimal.ZERO, true, null, true)));

    StartTimerRequest request = new StartTimerRequest("Звонок", new BigDecimal("3000"), true);
    assertThatThrownBy(() -> service.startTimer(caseId, request, lawyerId, List.of()))
        .isInstanceOf(TimerAlreadyRunningException.class);
    verify(timeEntryRepository, never()).saveAndFlush(any());
  }

  @Test
  void stopTimer_finalizesElapsedMinutes() {
    when(caseService.requireVisibleCase(caseId, lawyerId, List.of())).thenReturn(caseEntity());
    TimeEntry running = entry(0, new BigDecimal("3000"), true, null, true);
    running.setStartedAt(Instant.now().minus(25, ChronoUnit.MINUTES));
    when(timeEntryRepository.findByLawyerIdAndRunningTrue(lawyerId))
        .thenReturn(Optional.of(running));

    TimeEntryResponse response = service.stopTimer(caseId, lawyerId, List.of());

    assertThat(response.running()).isFalse();
    assertThat(response.minutes()).isEqualTo(25);
    assertThat(response.amount()).isEqualByComparingTo("1250.00");
  }

  private Case caseEntity() {
    Case caseEntity = new Case();
    caseEntity.setLawyerId(lawyerId);
    caseEntity.setClientId(clientId);
    setField(caseEntity, "id", caseId);
    return caseEntity;
  }

  private TimeEntry entry(
      int minutes, BigDecimal rate, boolean billable, UUID invoiceId, boolean running) {
    TimeEntry entry = new TimeEntry();
    setField(entry, "id", UUID.randomUUID());
    entry.setCaseId(caseId);
    entry.setClientId(clientId);
    entry.setLawyerId(lawyerId);
    entry.setDescription("Работа");
    entry.setActivityDate(LocalDate.of(2026, 7, 15));
    entry.setMinutes(minutes);
    entry.setHourlyRate(rate);
    entry.setBillable(billable);
    entry.setInvoiceId(invoiceId);
    entry.setRunning(running);
    return entry;
  }

  private void setField(Object target, String name, Object value) {
    try {
      Field field = target.getClass().getDeclaredField(name);
      field.setAccessible(true);
      field.set(target, value);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
  }
}
