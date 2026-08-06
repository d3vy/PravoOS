package com.pravoos.ai.court.api;

import com.pravoos.ai.shared.model.enums.CourtSystem;
import java.util.Optional;
import java.util.Set;

public interface CourtCaseLookup {

  boolean isEnabled(CourtSystem system);

  boolean hasAnyEnabled();

  Set<CourtSystem> enabledSystems();

  Optional<CourtCaseData> fetchCase(CourtSystem system, String caseNumber);
}
