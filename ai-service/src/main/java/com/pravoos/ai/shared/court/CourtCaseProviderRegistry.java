package com.pravoos.ai.shared.court;

import com.pravoos.ai.shared.model.enums.CourtSystem;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class CourtCaseProviderRegistry {

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

  public Optional<CourtCaseProvider> enabledFor(CourtSystem system) {
    return Optional.ofNullable(providersBySystem.get(system)).filter(CourtCaseProvider::isEnabled);
  }

  public boolean isEnabled(CourtSystem system) {
    return enabledFor(system).isPresent();
  }

  public boolean hasAnyEnabled() {
    return providersBySystem.values().stream().anyMatch(CourtCaseProvider::isEnabled);
  }

  public Set<CourtSystem> enabledSystems() {
    Set<CourtSystem> enabled = EnumSet.noneOf(CourtSystem.class);
    providersBySystem.values().stream()
        .filter(CourtCaseProvider::isEnabled)
        .forEach(provider -> enabled.add(provider.system()));
    return enabled;
  }
}
