package com.pravoos.ai.practice.internal.service;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.pravoos.ai.court.api.CourtCaseLookup;
import com.pravoos.ai.practice.internal.model.entity.Case;
import com.pravoos.ai.practice.internal.repository.jpa.CaseRepository;
import com.pravoos.ai.shared.model.enums.CourtSystem;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CourtPollingServiceTest {

  @Mock private CourtCaseLookup courtCaseLookup;
  @Mock private CaseRepository caseRepository;
  @Mock private CourtSyncService courtSyncService;

  private CourtPollingService service;

  @BeforeEach
  void setUp() {
    service = new CourtPollingService(courtCaseLookup, caseRepository, courtSyncService);
  }

  private Case caseWithSystem(CourtSystem courtSystem) {
    Case caseEntity = new Case();
    setId(caseEntity, UUID.randomUUID());
    caseEntity.setCourtSystem(courtSystem);
    return caseEntity;
  }

  private void setId(Case caseEntity, UUID id) {
    try {
      Field field = Case.class.getDeclaredField("id");
      field.setAccessible(true);
      field.set(caseEntity, id);
    } catch (ReflectiveOperationException e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  void skipsPollingWhenNoSystemsEnabled() {
    when(courtCaseLookup.enabledSystems()).thenReturn(Set.of());

    service.pollTrackedCases();

    verifyNoInteractions(caseRepository, courtSyncService);
  }

  @Test
  void skipsPollingWhenNoTrackedCasesMatchEnabledSystems() {
    when(courtCaseLookup.enabledSystems()).thenReturn(Set.of(CourtSystem.ARBITR));
    when(caseRepository.findByCourtCaseNumberIsNotNull())
        .thenReturn(List.of(caseWithSystem(CourtSystem.GENERAL_JURISDICTION)));

    service.pollTrackedCases();

    verifyNoInteractions(courtSyncService);
  }

  @Test
  void syncsOnlyCasesInEnabledSystems() {
    Case arbitrCase = caseWithSystem(CourtSystem.ARBITR);
    Case generalCase = caseWithSystem(CourtSystem.GENERAL_JURISDICTION);
    when(courtCaseLookup.enabledSystems()).thenReturn(Set.of(CourtSystem.ARBITR));
    when(caseRepository.findByCourtCaseNumberIsNotNull())
        .thenReturn(List.of(arbitrCase, generalCase));

    service.pollTrackedCases();

    verify(courtSyncService, times(1)).syncCase(arbitrCase.getId());
    verify(courtSyncService, never()).syncCase(generalCase.getId());
  }

  @Test
  void continuesPollingRemainingCasesWhenOneSyncFails() {
    Case first = caseWithSystem(CourtSystem.ARBITR);
    Case second = caseWithSystem(CourtSystem.ARBITR);
    when(courtCaseLookup.enabledSystems()).thenReturn(Set.of(CourtSystem.ARBITR));
    when(caseRepository.findByCourtCaseNumberIsNotNull()).thenReturn(List.of(first, second));
    org.mockito.Mockito.doThrow(new RuntimeException("boom"))
        .when(courtSyncService)
        .syncCase(first.getId());

    service.pollTrackedCases();

    verify(courtSyncService).syncCase(first.getId());
    verify(courtSyncService).syncCase(second.getId());
  }
}
