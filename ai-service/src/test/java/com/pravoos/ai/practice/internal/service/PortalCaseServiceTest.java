package com.pravoos.ai.practice.internal.service;

import com.pravoos.ai.practice.internal.dto.PortalCaseDetailResponse;
import com.pravoos.ai.practice.internal.dto.PortalCaseResponse;
import com.pravoos.ai.practice.internal.model.entity.Case;
import com.pravoos.ai.practice.internal.repository.jpa.CaseHearingEventRepository;
import com.pravoos.ai.practice.internal.repository.jpa.CaseRepository;
import com.pravoos.ai.shared.exception.CaseNotFoundException;
import com.pravoos.ai.shared.model.enums.CaseStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PortalCaseServiceTest {

    @Mock private CaseRepository caseRepository;
    @Mock private CaseHearingEventRepository hearingEventRepository;

    private PortalCaseService service() {
        return new PortalCaseService(caseRepository, hearingEventRepository);
    }

    private Case caseWithClient(UUID clientId) {
        Case caseEntity = new Case();
        caseEntity.setClientId(clientId);
        caseEntity.setTitle("Дело");
        caseEntity.setStatus(CaseStatus.IN_PROGRESS);
        return caseEntity;
    }

    @Test
    void findCases_returnsOnlyCasesInClientScope() {
        UUID clientId = UUID.randomUUID();
        when(caseRepository.findByClientIdInOrderByCreatedAtDesc(List.of(clientId)))
                .thenReturn(List.of(caseWithClient(clientId)));

        List<PortalCaseResponse> result = service().findCases(List.of(clientId));

        assertThat(result).singleElement()
                .satisfies(item -> assertThat(item.status()).isEqualTo(CaseStatus.IN_PROGRESS));
    }

    @Test
    void findCases_emptyScope_returnsEmpty_withoutQuery() {
        assertThat(service().findCases(List.of())).isEmpty();
        verifyNoInteractions(caseRepository);
    }

    @Test
    void getCase_returnsDetailWithHearings_whenInScope() {
        UUID clientId = UUID.randomUUID();
        UUID caseId = UUID.randomUUID();
        when(caseRepository.findById(caseId)).thenReturn(Optional.of(caseWithClient(clientId)));
        when(hearingEventRepository.findByCaseIdOrderByEventDateDescCreatedAtDesc(caseId))
                .thenReturn(List.of());

        PortalCaseDetailResponse response = service().getCase(caseId, List.of(clientId));

        assertThat(response.title()).isEqualTo("Дело");
        assertThat(response.hearings()).isEmpty();
    }

    @Test
    void getCase_throwsNotFound_whenCaseBelongsToAnotherClient() {
        UUID caseId = UUID.randomUUID();
        UUID foreignClientId = UUID.randomUUID();
        when(caseRepository.findById(caseId))
                .thenReturn(Optional.of(caseWithClient(foreignClientId)));

        assertThatThrownBy(() -> service().getCase(caseId, List.of(UUID.randomUUID())))
                .isInstanceOf(CaseNotFoundException.class);
        verifyNoInteractions(hearingEventRepository);
    }

    @Test
    void getCase_throwsNotFound_whenCaseHasNoClient() {
        UUID caseId = UUID.randomUUID();
        when(caseRepository.findById(caseId)).thenReturn(Optional.of(caseWithClient(null)));

        assertThatThrownBy(() -> service().getCase(caseId, List.of(UUID.randomUUID())))
                .isInstanceOf(CaseNotFoundException.class);
    }

    @Test
    void getCase_throwsNotFound_whenCaseMissing() {
        UUID caseId = UUID.randomUUID();
        when(caseRepository.findById(caseId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().getCase(caseId, List.of(UUID.randomUUID())))
                .isInstanceOf(CaseNotFoundException.class);
    }
}
