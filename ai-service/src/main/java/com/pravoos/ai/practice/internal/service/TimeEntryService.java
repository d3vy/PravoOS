package com.pravoos.ai.practice.internal.service;

import com.pravoos.ai.practice.internal.dto.CaseTimeSummary;
import com.pravoos.ai.practice.internal.dto.CreateTimeEntryRequest;
import com.pravoos.ai.practice.internal.dto.StartTimerRequest;
import com.pravoos.ai.practice.internal.dto.TimeEntryResponse;
import com.pravoos.ai.practice.internal.dto.UpdateTimeEntryRequest;
import com.pravoos.ai.practice.internal.model.entity.Case;
import com.pravoos.ai.practice.internal.model.entity.TimeEntry;
import com.pravoos.ai.practice.internal.repository.jpa.TimeEntryRepository;
import com.pravoos.ai.practice.internal.util.BillingAmounts;
import com.pravoos.ai.shared.exception.TimeEntryLockedException;
import com.pravoos.ai.shared.exception.TimeEntryNotFoundException;
import com.pravoos.ai.shared.exception.TimerAlreadyRunningException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class TimeEntryService {

    private static final Logger log = LoggerFactory.getLogger(TimeEntryService.class);

    private final TimeEntryRepository timeEntryRepository;
    private final CaseService caseService;

    public TimeEntryService(TimeEntryRepository timeEntryRepository, CaseService caseService) {
        this.timeEntryRepository = timeEntryRepository;
        this.caseService = caseService;
    }

    @Transactional
    public TimeEntryResponse create(UUID caseId, CreateTimeEntryRequest request, UUID lawyerId, List<UUID> orgIds) {
        Case caseEntity = caseService.requireVisibleCase(caseId, lawyerId, orgIds);

        TimeEntry entry = new TimeEntry();
        entry.setCaseId(caseId);
        entry.setClientId(caseEntity.getClientId());
        entry.setLawyerId(lawyerId);
        entry.setDescription(request.description().trim());
        entry.setActivityDate(request.activityDate());
        entry.setMinutes(request.minutes());
        entry.setHourlyRate(BillingAmounts.normalize(request.hourlyRate()));
        entry.setBillable(request.billable());

        TimeEntry saved = timeEntryRepository.save(entry);
        log.info("Time entry {} created on case {} ({} min) by lawyer {}", saved.getId(), caseId,
                saved.getMinutes(), lawyerId);
        return TimeEntryResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public CaseTimeSummary summary(UUID caseId, UUID lawyerId, List<UUID> orgIds) {
        caseService.requireVisibleCase(caseId, lawyerId, orgIds);
        List<TimeEntry> entries = timeEntryRepository.findByCaseIdOrderByActivityDateDescCreatedAtDesc(caseId);
        return buildSummary(entries);
    }

    @Transactional
    public TimeEntryResponse update(UUID caseId, UUID entryId, UpdateTimeEntryRequest request,
                                    UUID lawyerId, List<UUID> orgIds) {
        caseService.requireVisibleCase(caseId, lawyerId, orgIds);
        TimeEntry entry = requireEntryInCase(caseId, entryId);
        requireEditable(entry);

        entry.setDescription(request.description().trim());
        entry.setActivityDate(request.activityDate());
        entry.setMinutes(request.minutes());
        entry.setHourlyRate(BillingAmounts.normalize(request.hourlyRate()));
        entry.setBillable(request.billable());

        log.info("Time entry {} updated on case {} by lawyer {}", entryId, caseId, lawyerId);
        return TimeEntryResponse.from(entry);
    }

    @Transactional
    public void delete(UUID caseId, UUID entryId, UUID lawyerId, List<UUID> orgIds) {
        caseService.requireVisibleCase(caseId, lawyerId, orgIds);
        TimeEntry entry = requireEntryInCase(caseId, entryId);
        if (entry.isInvoiced()) {
            throw new TimeEntryLockedException(entryId);
        }
        timeEntryRepository.delete(entry);
        log.info("Time entry {} deleted on case {} by lawyer {}", entryId, caseId, lawyerId);
    }

    @Transactional
    public TimeEntryResponse startTimer(UUID caseId, StartTimerRequest request, UUID lawyerId, List<UUID> orgIds) {
        Case caseEntity = caseService.requireVisibleCase(caseId, lawyerId, orgIds);
        if (timeEntryRepository.findByLawyerIdAndRunningTrue(lawyerId).isPresent()) {
            throw new TimerAlreadyRunningException();
        }

        TimeEntry entry = new TimeEntry();
        entry.setCaseId(caseId);
        entry.setClientId(caseEntity.getClientId());
        entry.setLawyerId(lawyerId);
        entry.setDescription(request.description().trim());
        entry.setActivityDate(LocalDate.now(ZoneOffset.UTC));
        entry.setMinutes(0);
        entry.setHourlyRate(BillingAmounts.normalize(request.hourlyRate()));
        entry.setBillable(request.billable());
        entry.setRunning(true);
        entry.setStartedAt(Instant.now());

        try {
            TimeEntry saved = timeEntryRepository.saveAndFlush(entry);
            log.info("Timer {} started on case {} by lawyer {}", saved.getId(), caseId, lawyerId);
            return TimeEntryResponse.from(saved);
        } catch (DataIntegrityViolationException ex) {
            throw new TimerAlreadyRunningException();
        }
    }

    @Transactional
    public TimeEntryResponse stopTimer(UUID caseId, UUID lawyerId, List<UUID> orgIds) {
        caseService.requireVisibleCase(caseId, lawyerId, orgIds);
        TimeEntry entry = timeEntryRepository.findByLawyerIdAndRunningTrue(lawyerId)
                .filter(running -> running.getCaseId().equals(caseId))
                .orElseThrow(() -> new TimeEntryNotFoundException(caseId));

        entry.setMinutes(elapsedMinutes(entry.getStartedAt()));
        entry.setRunning(false);
        log.info("Timer {} stopped on case {} ({} min) by lawyer {}", entry.getId(), caseId,
                entry.getMinutes(), lawyerId);
        return TimeEntryResponse.from(entry);
    }

    @Transactional(readOnly = true)
    public Optional<TimeEntryResponse> activeTimer(UUID lawyerId) {
        return timeEntryRepository.findByLawyerIdAndRunningTrue(lawyerId).map(TimeEntryResponse::from);
    }

    private int elapsedMinutes(Instant startedAt) {
        if (startedAt == null) {
            return 1;
        }
        long seconds = Duration.between(startedAt, Instant.now()).getSeconds();
        return (int) Math.max(1, Math.round(seconds / 60.0));
    }

    private void requireEditable(TimeEntry entry) {
        if (entry.isInvoiced()) {
            throw new TimeEntryLockedException(entry.getId());
        }
        if (entry.isRunning()) {
            throw new TimeEntryLockedException(entry.getId());
        }
    }

    private TimeEntry requireEntryInCase(UUID caseId, UUID entryId) {
        TimeEntry entry = timeEntryRepository.findById(entryId)
                .orElseThrow(() -> new TimeEntryNotFoundException(entryId));
        if (!entry.getCaseId().equals(caseId)) {
            log.warn("Time entry {} does not belong to case {}", entryId, caseId);
            throw new TimeEntryNotFoundException(entryId);
        }
        return entry;
    }

    private CaseTimeSummary buildSummary(List<TimeEntry> entries) {
        int totalMinutes = 0;
        int billableMinutes = 0;
        int uninvoicedBillableMinutes = 0;
        BigDecimal billableAmount = BigDecimal.ZERO;
        BigDecimal uninvoicedBillableAmount = BigDecimal.ZERO;

        for (TimeEntry entry : entries) {
            if (entry.isRunning()) {
                continue;
            }
            totalMinutes += entry.getMinutes();
            if (entry.isBillable()) {
                BigDecimal amount = BillingAmounts.lineAmount(entry.getMinutes(), entry.getHourlyRate());
                billableMinutes += entry.getMinutes();
                billableAmount = billableAmount.add(amount);
                if (!entry.isInvoiced()) {
                    uninvoicedBillableMinutes += entry.getMinutes();
                    uninvoicedBillableAmount = uninvoicedBillableAmount.add(amount);
                }
            }
        }

        return new CaseTimeSummary(
                entries.stream().map(TimeEntryResponse::from).toList(),
                totalMinutes,
                billableMinutes,
                uninvoicedBillableMinutes,
                BillingAmounts.normalize(billableAmount),
                BillingAmounts.normalize(uninvoicedBillableAmount));
    }
}
