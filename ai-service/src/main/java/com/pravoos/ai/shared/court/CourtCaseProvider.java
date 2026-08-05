package com.pravoos.ai.shared.court;

import com.pravoos.ai.shared.model.enums.CourtSystem;
import java.util.Optional;

public interface CourtCaseProvider {

  CourtSystem system();

  boolean isEnabled();

  Optional<CourtCaseData> fetchCase(String caseNumber);
}
