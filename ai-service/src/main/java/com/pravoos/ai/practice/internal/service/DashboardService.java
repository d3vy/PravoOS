package com.pravoos.ai.practice.internal.service;

import com.pravoos.ai.practice.internal.dto.DashboardResponse;
import com.pravoos.ai.practice.internal.dto.DashboardResponse.MoneyOnTable;
import com.pravoos.ai.practice.internal.dto.DashboardResponse.RecentCase;
import com.pravoos.ai.practice.internal.dto.DashboardResponse.StatusCount;
import com.pravoos.ai.practice.internal.dto.DashboardResponse.TodayTask;
import com.pravoos.ai.practice.internal.dto.DashboardResponse.UnpaidInvoiceItem;
import com.pravoos.ai.practice.internal.dto.DashboardResponse.UnpaidInvoicesSummary;
import com.pravoos.ai.practice.internal.dto.DashboardResponse.UpcomingDeadline;
import com.pravoos.ai.practice.internal.model.entity.Case;
import com.pravoos.ai.practice.internal.model.entity.Client;
import com.pravoos.ai.practice.internal.model.entity.Invoice;
import com.pravoos.ai.practice.internal.model.entity.TimeEntry;
import com.pravoos.ai.practice.internal.repository.jpa.CaseRepository;
import com.pravoos.ai.practice.internal.repository.jpa.CaseTaskRepository;
import com.pravoos.ai.practice.internal.repository.jpa.ClientRepository;
import com.pravoos.ai.practice.internal.repository.jpa.InvoiceRepository;
import com.pravoos.ai.practice.internal.repository.jpa.TimeEntryRepository;
import com.pravoos.ai.practice.internal.util.BillingAmounts;
import com.pravoos.ai.shared.model.enums.CaseStatus;
import com.pravoos.ai.shared.model.enums.DeadlineType;
import com.pravoos.ai.shared.model.enums.InvoiceStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class DashboardService {

    private static final int DEADLINE_HORIZON_DAYS = 7;
    private static final int UNPAID_INVOICES_LIMIT = 5;
    private static final Set<CaseStatus> CLOSED_STATUSES =
            EnumSet.of(CaseStatus.CLOSED_WON, CaseStatus.CLOSED_LOST);

    private final CaseRepository caseRepository;
    private final CaseTaskRepository caseTaskRepository;
    private final TimeEntryRepository timeEntryRepository;
    private final InvoiceRepository invoiceRepository;
    private final ClientRepository clientRepository;

    public DashboardService(CaseRepository caseRepository,
                             CaseTaskRepository caseTaskRepository,
                             TimeEntryRepository timeEntryRepository,
                             InvoiceRepository invoiceRepository,
                             ClientRepository clientRepository) {
        this.caseRepository = caseRepository;
        this.caseTaskRepository = caseTaskRepository;
        this.timeEntryRepository = timeEntryRepository;
        this.invoiceRepository = invoiceRepository;
        this.clientRepository = clientRepository;
    }

    @Transactional(readOnly = true)
    public DashboardResponse getDashboard(UUID lawyerId) {
        return new DashboardResponse(
                buildPipeline(lawyerId),
                caseRepository.countByLawyerIdAndStatusNotIn(lawyerId, CLOSED_STATUSES),
                caseTaskRepository.countOpenByLawyerId(lawyerId),
                buildUpcomingDeadlines(lawyerId),
                buildRecentCases(lawyerId),
                buildMoneyOnTable(lawyerId),
                buildTasksToday(lawyerId),
                buildUnpaidInvoices(lawyerId)
        );
    }

    private MoneyOnTable buildMoneyOnTable(UUID lawyerId) {
        List<TimeEntry> entries = timeEntryRepository
                .findByLawyerIdAndBillableTrueAndInvoiceIdIsNullAndRunningFalse(lawyerId);
        int minutes = entries.stream().mapToInt(TimeEntry::getMinutes).sum();
        BigDecimal amount = entries.stream()
                .map(entry -> BillingAmounts.lineAmount(entry.getMinutes(), entry.getHourlyRate()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new MoneyOnTable(minutes, BillingAmounts.normalize(amount));
    }

    private List<TodayTask> buildTasksToday(UUID lawyerId) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        return caseTaskRepository.findDueTodayOrOverdueByLawyerId(lawyerId, CLOSED_STATUSES, today).stream()
                .map(task -> new TodayTask(
                        task.getId(),
                        task.getCaseId(),
                        task.getCaseTitle(),
                        task.getText(),
                        task.getDueDate(),
                        ChronoUnit.DAYS.between(task.getDueDate(), today)))
                .toList();
    }

    private UnpaidInvoicesSummary buildUnpaidInvoices(UUID lawyerId) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        List<Invoice> unpaid = invoiceRepository.findByLawyerIdAndStatusOrderByDueDateAsc(lawyerId, InvoiceStatus.ISSUED);
        Map<UUID, String> names = clientRepository.findAllById(unpaid.stream()
                        .map(Invoice::getClientId).distinct().toList()).stream()
                .collect(Collectors.toMap(Client::getId, Client::getName, (a, b) -> a));
        BigDecimal total = unpaid.stream().map(Invoice::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        List<UnpaidInvoiceItem> items = unpaid.stream()
                .limit(UNPAID_INVOICES_LIMIT)
                .map(invoice -> new UnpaidInvoiceItem(
                        invoice.getId(),
                        invoice.getNumber(),
                        names.get(invoice.getClientId()),
                        invoice.getTotal(),
                        invoice.getCurrency(),
                        invoice.getDueDate(),
                        invoice.getDueDate() == null ? 0 : ChronoUnit.DAYS.between(invoice.getDueDate(), today)))
                .toList();
        return new UnpaidInvoicesSummary(unpaid.size(), BillingAmounts.normalize(total), items);
    }

    private List<StatusCount> buildPipeline(UUID lawyerId) {
        Map<CaseStatus, Long> counts = caseRepository.countGroupedByStatus(lawyerId).stream()
                .collect(Collectors.toMap(CaseRepository.StatusCountView::getStatus,
                        CaseRepository.StatusCountView::getCount));
        List<StatusCount> pipeline = new ArrayList<>();
        for (CaseStatus status : CaseStatus.values()) {
            pipeline.add(new StatusCount(status, status.getDisplayName(), counts.getOrDefault(status, 0L)));
        }
        return pipeline;
    }

    private List<UpcomingDeadline> buildUpcomingDeadlines(UUID lawyerId) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate horizon = today.plusDays(DEADLINE_HORIZON_DAYS);
        List<UpcomingDeadline> deadlines = new ArrayList<>();
        for (Case caseEntity : caseRepository.findCasesWithUpcomingDeadlines(lawyerId, CLOSED_STATUSES, today, horizon)) {
            collectDeadline(deadlines, caseEntity, DeadlineType.FILING_DEADLINE, Case::getFilingDeadline, today, horizon);
            collectDeadline(deadlines, caseEntity, DeadlineType.NEXT_HEARING, Case::getNextHearingDate, today, horizon);
            collectDeadline(deadlines, caseEntity, DeadlineType.EXPIRY, Case::getExpiresAt, today, horizon);
        }
        for (CaseTaskRepository.UpcomingTaskView task
                : caseTaskRepository.findUpcomingByLawyerId(lawyerId, CLOSED_STATUSES, today, horizon)) {
            deadlines.add(new UpcomingDeadline(
                    task.getCaseId(),
                    task.getCaseTitle(),
                    DeadlineType.TASK,
                    DeadlineType.TASK.getDisplayName(),
                    task.getDueDate(),
                    ChronoUnit.DAYS.between(today, task.getDueDate())
            ));
        }
        deadlines.sort(Comparator.comparing(UpcomingDeadline::date));
        return deadlines;
    }

    private void collectDeadline(List<UpcomingDeadline> deadlines,
                                 Case caseEntity,
                                 DeadlineType type,
                                 Function<Case, LocalDate> dateAccessor,
                                 LocalDate today,
                                 LocalDate horizon) {
        LocalDate date = dateAccessor.apply(caseEntity);
        if (date == null || date.isBefore(today) || date.isAfter(horizon)) {
            return;
        }
        deadlines.add(new UpcomingDeadline(
                caseEntity.getId(),
                caseEntity.getTitle(),
                type,
                type.getDisplayName(),
                date,
                ChronoUnit.DAYS.between(today, date)
        ));
    }

    private List<RecentCase> buildRecentCases(UUID lawyerId) {
        return caseRepository.findTop5ByLawyerIdOrderByCreatedAtDesc(lawyerId).stream()
                .map(caseEntity -> new RecentCase(
                        caseEntity.getId(),
                        caseEntity.getTitle(),
                        caseEntity.getStatus(),
                        caseEntity.getStatus().getDisplayName(),
                        caseEntity.getCreatedAt()))
                .toList();
    }
}
