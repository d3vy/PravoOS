package com.pravoos.ai.practice.internal.repository.jpa;

import com.pravoos.ai.practice.internal.model.entity.CaseHearingEvent;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CaseHearingEventRepository extends JpaRepository<CaseHearingEvent, UUID> {

  boolean existsByCaseIdAndSourceEventId(UUID caseId, String sourceEventId);

  List<CaseHearingEvent> findByCaseIdOrderByEventDateDescCreatedAtDesc(UUID caseId);

  List<CaseHearingEvent> findByCaseIdInAndEventDateBetween(
      Collection<UUID> caseIds, LocalDate from, LocalDate to);
}
