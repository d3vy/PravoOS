package com.pravoos.ai.practice.internal.service;

import com.pravoos.ai.arbitr.ArbitrCaseProvider;
import com.pravoos.ai.practice.internal.model.entity.Case;
import com.pravoos.ai.practice.internal.repository.jpa.CaseRepository;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class ArbitrPollingService {

    private static final Logger log = LoggerFactory.getLogger(ArbitrPollingService.class);

    private final ArbitrCaseProvider arbitrCaseProvider;
    private final CaseRepository caseRepository;
    private final ArbitrSyncService arbitrSyncService;

    public ArbitrPollingService(ArbitrCaseProvider arbitrCaseProvider,
                                CaseRepository caseRepository,
                                ArbitrSyncService arbitrSyncService) {
        this.arbitrCaseProvider = arbitrCaseProvider;
        this.caseRepository = caseRepository;
        this.arbitrSyncService = arbitrSyncService;
    }

    @Scheduled(cron = "${arbitr.poll.cron:0 0 */6 * * *}", zone = "UTC")
    @SchedulerLock(name = "ArbitrPollingService_pollTrackedCases", lockAtLeastFor = "PT1M", lockAtMostFor = "PT2H")
    public void pollTrackedCases() {
        if (!arbitrCaseProvider.isEnabled()) {
            return;
        }
        List<UUID> caseIds = caseRepository.findByArbitrCaseNumberIsNotNull()
                .stream()
                .map(Case::getId)
                .toList();
        if (caseIds.isEmpty()) {
            return;
        }
        log.info("КАД.Арбитр polling: обрабатываю {} дел", caseIds.size());
        int failed = 0;
        for (UUID caseId : caseIds) {
            try {
                arbitrSyncService.syncCase(caseId);
            } catch (Exception e) {
                failed++;
                log.error("КАД.Арбитр polling: дело {} не обновлено: {}", caseId, e.getMessage());
            }
        }
        log.info("КАД.Арбитр polling завершён: {} дел, ошибок {}", caseIds.size(), failed);
    }
}
