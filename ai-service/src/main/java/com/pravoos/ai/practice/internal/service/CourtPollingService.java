package com.pravoos.ai.practice.internal.service;

import com.pravoos.ai.court.api.CourtCaseLookup;
import com.pravoos.ai.practice.internal.model.entity.Case;
import com.pravoos.ai.practice.internal.repository.jpa.CaseRepository;
import com.pravoos.ai.shared.model.enums.CourtSystem;
import java.util.Set;
import java.util.UUID;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class CourtPollingService {

  private static final Logger log = LoggerFactory.getLogger(CourtPollingService.class);
  private static final int PAGE_SIZE = 500;

  private final CourtCaseLookup courtCaseLookup;
  private final CaseRepository caseRepository;
  private final CourtSyncService courtSyncService;

  public CourtPollingService(
      CourtCaseLookup courtCaseLookup,
      CaseRepository caseRepository,
      CourtSyncService courtSyncService) {
    this.courtCaseLookup = courtCaseLookup;
    this.caseRepository = caseRepository;
    this.courtSyncService = courtSyncService;
  }

  @Scheduled(cron = "${court.poll.cron:0 0 */6 * * *}", zone = "UTC")
  @SchedulerLock(
      name = "CourtPollingService_pollTrackedCases",
      lockAtLeastFor = "PT1M",
      lockAtMostFor = "PT2H")
  public void pollTrackedCases() {
    Set<CourtSystem> enabledSystems = courtCaseLookup.enabledSystems();
    if (enabledSystems.isEmpty()) {
      return;
    }
    log.info("Опрос судов ({}): начинаю обход дел", enabledSystems);
    int total = 0;
    int failed = 0;
    Pageable pageable = PageRequest.of(0, PAGE_SIZE);
    Page<Case> page;
    do {
      page =
          caseRepository.findByCourtCaseNumberIsNotNullAndCourtSystemIn(enabledSystems, pageable);
      for (Case caseEntity : page) {
        UUID caseId = caseEntity.getId();
        try {
          courtSyncService.syncCase(caseId);
        } catch (Exception e) {
          failed++;
          log.error("Опрос судов: дело {} не обновлено: {}", caseId, e.getMessage());
        }
      }
      total += page.getNumberOfElements();
      pageable = pageable.next();
    } while (page.hasNext());
    log.info("Опрос судов завершён: {} дел, ошибок {}", total, failed);
  }
}
