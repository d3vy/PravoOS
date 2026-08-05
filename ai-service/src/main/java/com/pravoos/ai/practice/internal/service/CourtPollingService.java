package com.pravoos.ai.practice.internal.service;

import com.pravoos.ai.practice.internal.model.entity.Case;
import com.pravoos.ai.practice.internal.repository.jpa.CaseRepository;
import com.pravoos.ai.shared.court.CourtCaseProviderRegistry;
import com.pravoos.ai.shared.model.enums.CourtSystem;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class CourtPollingService {

  private static final Logger log = LoggerFactory.getLogger(CourtPollingService.class);

  private final CourtCaseProviderRegistry courtCaseProviderRegistry;
  private final CaseRepository caseRepository;
  private final CourtSyncService courtSyncService;

  public CourtPollingService(
      CourtCaseProviderRegistry courtCaseProviderRegistry,
      CaseRepository caseRepository,
      CourtSyncService courtSyncService) {
    this.courtCaseProviderRegistry = courtCaseProviderRegistry;
    this.caseRepository = caseRepository;
    this.courtSyncService = courtSyncService;
  }

  @Scheduled(cron = "${court.poll.cron:0 0 */6 * * *}", zone = "UTC")
  @SchedulerLock(
      name = "CourtPollingService_pollTrackedCases",
      lockAtLeastFor = "PT1M",
      lockAtMostFor = "PT2H")
  public void pollTrackedCases() {
    Set<CourtSystem> enabledSystems = courtCaseProviderRegistry.enabledSystems();
    if (enabledSystems.isEmpty()) {
      return;
    }
    List<UUID> caseIds =
        caseRepository.findByCourtCaseNumberIsNotNull().stream()
            .filter(caseEntity -> enabledSystems.contains(caseEntity.getCourtSystem()))
            .map(Case::getId)
            .toList();
    if (caseIds.isEmpty()) {
      return;
    }
    log.info("Опрос судов ({}): обрабатываю {} дел", enabledSystems, caseIds.size());
    int failed = 0;
    for (UUID caseId : caseIds) {
      try {
        courtSyncService.syncCase(caseId);
      } catch (Exception e) {
        failed++;
        log.error("Опрос судов: дело {} не обновлено: {}", caseId, e.getMessage());
      }
    }
    log.info("Опрос судов завершён: {} дел, ошибок {}", caseIds.size(), failed);
  }
}
