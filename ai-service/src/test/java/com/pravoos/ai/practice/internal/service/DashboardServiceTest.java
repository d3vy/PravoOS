package com.pravoos.ai.practice.internal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.pravoos.ai.practice.internal.dto.DashboardResponse;
import com.pravoos.ai.practice.internal.model.entity.Case;
import com.pravoos.ai.practice.internal.model.entity.Client;
import com.pravoos.ai.practice.internal.model.entity.Invoice;
import com.pravoos.ai.practice.internal.model.entity.TimeEntry;
import com.pravoos.ai.practice.internal.repository.jpa.CaseRepository;
import com.pravoos.ai.practice.internal.repository.jpa.CaseTaskRepository;
import com.pravoos.ai.practice.internal.repository.jpa.ClientRepository;
import com.pravoos.ai.practice.internal.repository.jpa.InvoiceRepository;
import com.pravoos.ai.practice.internal.repository.jpa.TimeEntryRepository;
import com.pravoos.ai.shared.model.enums.CaseStatus;
import com.pravoos.ai.shared.model.enums.DeadlineType;
import com.pravoos.ai.shared.model.enums.InvoiceStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

  @Mock private CaseRepository caseRepository;
  @Mock private CaseTaskRepository caseTaskRepository;
  @Mock private TimeEntryRepository timeEntryRepository;
  @Mock private InvoiceRepository invoiceRepository;
  @Mock private ClientRepository clientRepository;

  @InjectMocks private DashboardService dashboardService;

  private final UUID lawyerId = UUID.randomUUID();
  private final LocalDate today = LocalDate.now(ZoneOffset.UTC);
  private final Set<CaseStatus> closedStatuses =
      EnumSet.of(CaseStatus.CLOSED_WON, CaseStatus.CLOSED_LOST);

  private void stubEmptyDefaults() {
    when(caseRepository.countGroupedByStatus(lawyerId)).thenReturn(List.of());
    when(caseRepository.countByLawyerIdAndStatusNotIn(any(), anyCollection())).thenReturn(0L);
    when(caseTaskRepository.countOpenByLawyerId(lawyerId)).thenReturn(0L);
    org.mockito.Mockito.lenient()
        .when(caseRepository.findCasesWithUpcomingDeadlines(any(), anyCollection(), any(), any()))
        .thenReturn(List.of());
    org.mockito.Mockito.lenient()
        .when(caseTaskRepository.findUpcomingByLawyerId(any(), anyCollection(), any(), any()))
        .thenReturn(List.of());
    when(caseRepository.findTop5ByLawyerIdOrderByCreatedAtDesc(lawyerId)).thenReturn(List.of());
    when(timeEntryRepository.findByLawyerIdAndBillableTrueAndInvoiceIdIsNullAndRunningFalse(
            lawyerId))
        .thenReturn(List.of());
    org.mockito.Mockito.lenient()
        .when(caseTaskRepository.findDueTodayOrOverdueByLawyerId(any(), anyCollection(), any()))
        .thenReturn(List.of());
    when(invoiceRepository.findByLawyerIdAndStatusOrderByDueDateAsc(lawyerId, InvoiceStatus.ISSUED))
        .thenReturn(List.of());
  }

  private Case caseWithId(UUID id, String title) {
    Case caseEntity = new Case();
    ReflectionTestUtils.setField(caseEntity, "id", id);
    caseEntity.setTitle(title);
    return caseEntity;
  }

  @Test
  void getDashboardReturnsAllZerosAndEmptyListsWhenNothingFound() {
    stubEmptyDefaults();

    DashboardResponse response = dashboardService.getDashboard(lawyerId);

    assertThat(response.pipeline()).hasSize(CaseStatus.values().length);
    assertThat(response.pipeline()).allSatisfy(sc -> assertThat(sc.count()).isZero());
    assertThat(response.activeCases()).isZero();
    assertThat(response.openTasks()).isZero();
    assertThat(response.upcomingDeadlines()).isEmpty();
    assertThat(response.recentCases()).isEmpty();
    assertThat(response.moneyOnTable().uninvoicedMinutes()).isZero();
    assertThat(response.moneyOnTable().uninvoicedAmount())
        .isEqualByComparingTo(BigDecimal.ZERO.setScale(2));
    assertThat(response.tasksToday()).isEmpty();
    assertThat(response.unpaidInvoices().count()).isZero();
    assertThat(response.unpaidInvoices().items()).isEmpty();
  }

  @Test
  void getDashboardDelegatesActiveCasesAndOpenTasksCounts() {
    stubEmptyDefaults();
    when(caseRepository.countByLawyerIdAndStatusNotIn(lawyerId, closedStatuses)).thenReturn(7L);
    when(caseTaskRepository.countOpenByLawyerId(lawyerId)).thenReturn(3L);

    DashboardResponse response = dashboardService.getDashboard(lawyerId);

    assertThat(response.activeCases()).isEqualTo(7L);
    assertThat(response.openTasks()).isEqualTo(3L);
  }

  @Test
  void pipelineFillsMissingStatusesWithZeroAndPreservesEnumOrder() {
    stubEmptyDefaults();
    CaseRepository.StatusCountView inProgress = mock(CaseRepository.StatusCountView.class);
    when(inProgress.getStatus()).thenReturn(CaseStatus.IN_PROGRESS);
    when(inProgress.getCount()).thenReturn(5L);
    when(caseRepository.countGroupedByStatus(lawyerId)).thenReturn(List.of(inProgress));

    DashboardResponse response = dashboardService.getDashboard(lawyerId);

    assertThat(response.pipeline()).hasSize(CaseStatus.values().length);
    assertThat(response.pipeline())
        .extracting(DashboardResponse.StatusCount::status)
        .containsExactly(CaseStatus.values());
    assertThat(response.pipeline())
        .filteredOn(sc -> sc.status() == CaseStatus.IN_PROGRESS)
        .singleElement()
        .satisfies(
            sc -> {
              assertThat(sc.count()).isEqualTo(5L);
              assertThat(sc.statusName()).isEqualTo(CaseStatus.IN_PROGRESS.getDisplayName());
            });
    assertThat(response.pipeline())
        .filteredOn(sc -> sc.status() == CaseStatus.INTAKE)
        .singleElement()
        .satisfies(sc -> assertThat(sc.count()).isZero());
  }

  @Test
  void upcomingDeadlinesCollectsAllThreeTypesWithinHorizonAndTasksSortedByDate() {
    stubEmptyDefaults();
    UUID caseId = UUID.randomUUID();
    Case caseEntity = caseWithId(caseId, "Дело №1");
    caseEntity.setFilingDeadline(today.plusDays(2));
    caseEntity.setNextHearingDate(today.plusDays(10)); // outside 7-day horizon
    caseEntity.setExpiresAt(today.minusDays(1)); // before today, excluded
    when(caseRepository.findCasesWithUpcomingDeadlines(
            eq(lawyerId), eq(closedStatuses), eq(today), eq(today.plusDays(7))))
        .thenReturn(List.of(caseEntity));

    CaseTaskRepository.UpcomingTaskView taskView = mock(CaseTaskRepository.UpcomingTaskView.class);
    UUID taskCaseId = UUID.randomUUID();
    when(taskView.getCaseId()).thenReturn(taskCaseId);
    when(taskView.getCaseTitle()).thenReturn("Дело задачи");
    when(taskView.getDueDate()).thenReturn(today.plusDays(1));
    when(caseTaskRepository.findUpcomingByLawyerId(
            eq(lawyerId), eq(closedStatuses), eq(today), eq(today.plusDays(7))))
        .thenReturn(List.of(taskView));

    DashboardResponse response = dashboardService.getDashboard(lawyerId);

    assertThat(response.upcomingDeadlines()).hasSize(2);
    assertThat(response.upcomingDeadlines().get(0).date()).isEqualTo(today.plusDays(1));
    assertThat(response.upcomingDeadlines().get(0).type()).isEqualTo(DeadlineType.TASK);
    assertThat(response.upcomingDeadlines().get(0).caseId()).isEqualTo(taskCaseId);
    assertThat(response.upcomingDeadlines().get(0).caseTitle()).isEqualTo("Дело задачи");
    assertThat(response.upcomingDeadlines().get(0).daysLeft()).isEqualTo(1L);

    assertThat(response.upcomingDeadlines().get(1).date()).isEqualTo(today.plusDays(2));
    assertThat(response.upcomingDeadlines().get(1).type()).isEqualTo(DeadlineType.FILING_DEADLINE);
    assertThat(response.upcomingDeadlines().get(1).caseId()).isEqualTo(caseId);
    assertThat(response.upcomingDeadlines().get(1).daysLeft()).isEqualTo(2L);
  }

  @Test
  void recentCasesMapsFieldsFromRepository() {
    stubEmptyDefaults();
    UUID caseId = UUID.randomUUID();
    Case caseEntity = caseWithId(caseId, "Свежее дело");
    caseEntity.setStatus(CaseStatus.SUBMITTED);
    when(caseRepository.findTop5ByLawyerIdOrderByCreatedAtDesc(lawyerId))
        .thenReturn(List.of(caseEntity));

    DashboardResponse response = dashboardService.getDashboard(lawyerId);

    assertThat(response.recentCases()).hasSize(1);
    var recent = response.recentCases().get(0);
    assertThat(recent.id()).isEqualTo(caseId);
    assertThat(recent.title()).isEqualTo("Свежее дело");
    assertThat(recent.status()).isEqualTo(CaseStatus.SUBMITTED);
    assertThat(recent.statusName()).isEqualTo(CaseStatus.SUBMITTED.getDisplayName());
    assertThat(recent.createdAt()).isEqualTo(caseEntity.getCreatedAt());
  }

  @Test
  void moneyOnTableSumsMinutesAndComputesAmountViaBillingAmounts() {
    stubEmptyDefaults();
    TimeEntry entryOne = new TimeEntry();
    entryOne.setMinutes(60);
    entryOne.setHourlyRate(BigDecimal.valueOf(1000));
    TimeEntry entryTwo = new TimeEntry();
    entryTwo.setMinutes(30);
    entryTwo.setHourlyRate(BigDecimal.valueOf(1000));
    when(timeEntryRepository.findByLawyerIdAndBillableTrueAndInvoiceIdIsNullAndRunningFalse(
            lawyerId))
        .thenReturn(List.of(entryOne, entryTwo));

    DashboardResponse response = dashboardService.getDashboard(lawyerId);

    assertThat(response.moneyOnTable().uninvoicedMinutes()).isEqualTo(90);
    assertThat(response.moneyOnTable().uninvoicedAmount())
        .isEqualByComparingTo(BigDecimal.valueOf(1500).setScale(2));
  }

  @Test
  void tasksTodayMapsFieldsAndComputesDaysOverdue() {
    stubEmptyDefaults();
    CaseTaskRepository.TodayTaskView todayTask = mock(CaseTaskRepository.TodayTaskView.class);
    UUID taskId = UUID.randomUUID();
    UUID taskCaseId = UUID.randomUUID();
    LocalDate dueDate = today.minusDays(3);
    when(todayTask.getId()).thenReturn(taskId);
    when(todayTask.getCaseId()).thenReturn(taskCaseId);
    when(todayTask.getCaseTitle()).thenReturn("Просроченное дело");
    when(todayTask.getText()).thenReturn("Подать возражение");
    when(todayTask.getDueDate()).thenReturn(dueDate);
    when(caseTaskRepository.findDueTodayOrOverdueByLawyerId(
            eq(lawyerId), eq(closedStatuses), eq(today)))
        .thenReturn(List.of(todayTask));

    DashboardResponse response = dashboardService.getDashboard(lawyerId);

    assertThat(response.tasksToday()).hasSize(1);
    var task = response.tasksToday().get(0);
    assertThat(task.id()).isEqualTo(taskId);
    assertThat(task.caseId()).isEqualTo(taskCaseId);
    assertThat(task.caseTitle()).isEqualTo("Просроченное дело");
    assertThat(task.text()).isEqualTo("Подать возражение");
    assertThat(task.dueDate()).isEqualTo(dueDate);
    assertThat(task.daysOverdue()).isEqualTo(ChronoUnit.DAYS.between(dueDate, today));
  }

  @Test
  void unpaidInvoicesResolvesClientNamesTotalsAndLimitsItems() {
    stubEmptyDefaults();
    UUID clientId = UUID.randomUUID();
    Client client = new Client();
    ReflectionTestUtils.setField(client, "id", clientId);
    client.setName("ООО Клиент");
    when(clientRepository.findAllById(anyCollection())).thenReturn(List.of(client));

    List<Invoice> invoices =
        java.util.stream.IntStream.range(0, 7)
            .mapToObj(
                i -> {
                  Invoice invoice = new Invoice();
                  ReflectionTestUtils.setField(invoice, "id", UUID.randomUUID());
                  invoice.setNumber("INV-" + i);
                  invoice.setClientId(clientId);
                  invoice.setTotal(BigDecimal.valueOf(100 + i));
                  invoice.setCurrency("RUB");
                  invoice.setDueDate(i == 0 ? null : today.minusDays(i));
                  return invoice;
                })
            .toList();
    when(invoiceRepository.findByLawyerIdAndStatusOrderByDueDateAsc(lawyerId, InvoiceStatus.ISSUED))
        .thenReturn(invoices);

    DashboardResponse response = dashboardService.getDashboard(lawyerId);

    assertThat(response.unpaidInvoices().count()).isEqualTo(7L);
    BigDecimal expectedTotal =
        invoices.stream()
            .map(Invoice::getTotal)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .setScale(2);
    assertThat(response.unpaidInvoices().totalAmount()).isEqualByComparingTo(expectedTotal);
    assertThat(response.unpaidInvoices().items()).hasSize(5);

    var itemWithNullDueDate = response.unpaidInvoices().items().get(0);
    assertThat(itemWithNullDueDate.dueDate()).isNull();
    assertThat(itemWithNullDueDate.daysOverdue()).isZero();
    assertThat(itemWithNullDueDate.clientName()).isEqualTo("ООО Клиент");

    var itemWithDueDate = response.unpaidInvoices().items().get(1);
    assertThat(itemWithDueDate.daysOverdue())
        .isEqualTo(ChronoUnit.DAYS.between(itemWithDueDate.dueDate(), today));
  }
}
