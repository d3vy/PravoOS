package com.pravoos.ai.court.internal;

import com.pravoos.ai.court.api.CourtCaseData;
import com.pravoos.ai.court.api.CourtCaseLookup;
import com.pravoos.ai.shared.model.enums.CourtSystem;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class CourtCaseProviderRegistry implements CourtCaseLookup {

  private final Map<CourtSystem, CourtCaseProvider> providersBySystem;

  public CourtCaseProviderRegistry(List<CourtCaseProvider> providers) {
    Map<CourtSystem, CourtCaseProvider> resolved = new EnumMap<>(CourtSystem.class);
    for (CourtCaseProvider provider : providers) {
      CourtCaseProvider previous = resolved.put(provider.system(), provider);
      if (previous != null) {
        throw new IllegalStateException(
            "Для судебной системы "
                + provider.system()
                + " зарегистрировано несколько провайдеров: "
                + previous.getClass().getSimpleName()
                + " и "
                + provider.getClass().getSimpleName());
      }
    }
    this.providersBySystem = Map.copyOf(resolved);
  }

  Optional<CourtCaseProvider> enabledFor(CourtSystem system) {
    return Optional.ofNullable(providersBySystem.get(system)).filter(CourtCaseProvider::isEnabled);
  }

  @Override
  public Optional<CourtCaseData> fetchCase(CourtSystem system, String caseNumber) {
    return enabledFor(system).flatMap(provider -> provider.fetchCase(caseNumber));
  }

  @Override
  public boolean isEnabled(CourtSystem system) {
    return enabledFor(system).isPresent();
  }

  @Override
  public boolean hasAnyEnabled() {
    return providersBySystem.values().stream().anyMatch(CourtCaseProvider::isEnabled);
  }

  @Override
  public Set<CourtSystem> enabledSystems() {
    Set<CourtSystem> enabled = EnumSet.noneOf(CourtSystem.class);
    providersBySystem.values().stream()
        .filter(CourtCaseProvider::isEnabled)
        .forEach(provider -> enabled.add(provider.system()));
    return enabled;
  }
}
