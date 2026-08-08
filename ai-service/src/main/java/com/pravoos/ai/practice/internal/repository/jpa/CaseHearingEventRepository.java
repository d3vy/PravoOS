package com.pravoos.ai.practice.internal.repository.jpa;

import com.pravoos.ai.practice.internal.model.entity.CaseHearingEvent;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CaseHearingEventRepository extends JpaRepository<CaseHearingEvent, UUID> {

  @Query(
      "SELECT e.sourceEventId FROM CaseHearingEvent e "
          + "WHERE e.caseId = :caseId AND e.sourceEventId IS NOT NULL")
  List<String> findSourceEventIdsByCaseId(@Param("caseId") UUID caseId);

  List<CaseHearingEvent> findByCaseIdOrderByEventDateDescCreatedAtDesc(UUID caseId);

  List<CaseHearingEvent> findByCaseIdInAndEventDateBetween(
      Collection<UUID> caseIds, LocalDate from, LocalDate to);
}
