package com.pravoos.ai.practice.internal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pravoos.ai.practice.internal.model.entity.Case;
import com.pravoos.ai.practice.internal.repository.jpa.CaseRepository;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class CaseAccessProviderImplTest {

  @Mock private CaseService caseService;
  @Mock private CaseRepository caseRepository;

  @InjectMocks private CaseAccessProviderImpl caseAccessProvider;

  @Test
  void assertCaseVisibleDelegatesToCaseService() {
    UUID caseId = UUID.randomUUID();
    UUID lawyerId = UUID.randomUUID();
    List<UUID> orgIds = List.of(UUID.randomUUID());

    caseAccessProvider.assertCaseVisible(caseId, lawyerId, orgIds);

    verify(caseService).requireVisibleCase(caseId, lawyerId, orgIds);
  }

  @Test
  void assertCaseOwnedDelegatesToCaseService() {
    UUID caseId = UUID.randomUUID();
    UUID lawyerId = UUID.randomUUID();

    caseAccessProvider.assertCaseOwned(caseId, lawyerId);

    verify(caseService).requireOwnedCase(caseId, lawyerId);
  }

  @Test
  void retainCasesOwnedByReturnsEmptySetForNullInput() {
    Set<UUID> result = caseAccessProvider.retainCasesOwnedBy(null, UUID.randomUUID());

    assertThat(result).isEmpty();
  }

  @Test
  void retainCasesOwnedByReturnsEmptySetForEmptyInput() {
    Set<UUID> result = caseAccessProvider.retainCasesOwnedBy(Set.of(), UUID.randomUUID());

    assertThat(result).isEmpty();
  }

  @Test
  void retainCasesOwnedByKeepsOnlyCasesOwnedByLawyer() {
    UUID lawyerId = UUID.randomUUID();
    UUID otherLawyerId = UUID.randomUUID();

    Case ownedCase = new Case();
    ReflectionTestUtils.setField(ownedCase, "id", UUID.randomUUID());
    ownedCase.setLawyerId(lawyerId);

    Case foreignCase = new Case();
    ReflectionTestUtils.setField(foreignCase, "id", UUID.randomUUID());
    foreignCase.setLawyerId(otherLawyerId);

    Set<UUID> requestedIds = Set.of(ownedCase.getId(), foreignCase.getId());
    when(caseRepository.findAllById(requestedIds)).thenReturn(List.of(ownedCase, foreignCase));

    Set<UUID> result = caseAccessProvider.retainCasesOwnedBy(requestedIds, lawyerId);

    assertThat(result).containsExactly(ownedCase.getId());
  }
}
