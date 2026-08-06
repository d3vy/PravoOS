package com.pravoos.ai.court.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pravoos.ai.court.api.CourtCaseData;
import com.pravoos.ai.shared.model.enums.CourtSystem;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CourtCaseProviderRegistryTest {

  private static final CourtCaseData SAMPLE =
      new CourtCaseData("А40-1/2026", null, LocalDate.now(), null, List.of(), List.of());

  private static class StubProvider implements CourtCaseProvider {
    private final CourtSystem system;
    private final boolean enabled;

    StubProvider(CourtSystem system, boolean enabled) {
      this.system = system;
      this.enabled = enabled;
    }

    @Override
    public CourtSystem system() {
      return system;
    }

    @Override
    public boolean isEnabled() {
      return enabled;
    }

    @Override
    public Optional<CourtCaseData> fetchCase(String caseNumber) {
      return Optional.of(SAMPLE);
    }
  }

  @Test
  void fetchesCaseThroughEnabledProviderOnly() {
    CourtCaseProviderRegistry registry =
        new CourtCaseProviderRegistry(
            List.of(
                new StubProvider(CourtSystem.ARBITR, true),
                new StubProvider(CourtSystem.GENERAL_JURISDICTION, false)));

    assertThat(registry.fetchCase(CourtSystem.ARBITR, "А40-1/2026")).contains(SAMPLE);
    assertThat(registry.fetchCase(CourtSystem.GENERAL_JURISDICTION, "2-1/2026")).isEmpty();
  }

  @Test
  void returnsProviderOnlyWhenItIsEnabled() {
    CourtCaseProviderRegistry registry =
        new CourtCaseProviderRegistry(
            List.of(
                new StubProvider(CourtSystem.ARBITR, true),
                new StubProvider(CourtSystem.GENERAL_JURISDICTION, false)));

    assertThat(registry.enabledFor(CourtSystem.ARBITR)).isPresent();
    assertThat(registry.enabledFor(CourtSystem.GENERAL_JURISDICTION)).isEmpty();
    assertThat(registry.isEnabled(CourtSystem.ARBITR)).isTrue();
  }

  @Test
  void reportsEnabledSystems() {
    CourtCaseProviderRegistry registry =
        new CourtCaseProviderRegistry(
            List.of(
                new StubProvider(CourtSystem.ARBITR, true),
                new StubProvider(CourtSystem.GENERAL_JURISDICTION, true)));

    assertThat(registry.enabledSystems())
        .containsExactlyInAnyOrder(CourtSystem.ARBITR, CourtSystem.GENERAL_JURISDICTION);
    assertThat(registry.hasAnyEnabled()).isTrue();
  }

  @Test
  void reportsNothingEnabledWhenAllProvidersAreOff() {
    CourtCaseProviderRegistry registry =
        new CourtCaseProviderRegistry(List.of(new NoopCourtCaseProvider(CourtSystem.ARBITR)));

    assertThat(registry.hasAnyEnabled()).isFalse();
    assertThat(registry.enabledSystems()).isEmpty();
  }

  @Test
  void failsFastWhenTwoProvidersClaimTheSameCourtSystem() {
    List<CourtCaseProvider> duplicates =
        List.of(
            new StubProvider(CourtSystem.ARBITR, true), new StubProvider(CourtSystem.ARBITR, true));

    assertThatThrownBy(() -> new CourtCaseProviderRegistry(duplicates))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("ARBITR");
  }

  @Test
  void returnsEmptyForSystemWithoutAnyProvider() {
    CourtCaseProviderRegistry registry =
        new CourtCaseProviderRegistry(List.of(new StubProvider(CourtSystem.ARBITR, true)));

    assertThat(registry.enabledFor(CourtSystem.GENERAL_JURISDICTION)).isEmpty();
  }
}
