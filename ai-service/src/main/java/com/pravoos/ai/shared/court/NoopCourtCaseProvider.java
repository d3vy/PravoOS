package com.pravoos.ai.shared.court;

import com.pravoos.ai.shared.model.enums.CourtSystem;
import java.util.Optional;

public class NoopCourtCaseProvider implements CourtCaseProvider {

  private final CourtSystem system;

  public NoopCourtCaseProvider(CourtSystem system) {
    this.system = system;
  }

  @Override
  public CourtSystem system() {
    return system;
  }

  @Override
  public boolean isEnabled() {
    return false;
  }

  @Override
  public Optional<CourtCaseData> fetchCase(String caseNumber) {
    return Optional.empty();
  }
}
