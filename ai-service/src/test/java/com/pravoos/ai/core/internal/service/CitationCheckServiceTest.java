package com.pravoos.ai.core.internal.service;

import com.pravoos.ai.core.internal.dto.CitationCheckResult;
import com.pravoos.ai.core.internal.model.entity.AiResponse;
import com.pravoos.ai.core.internal.repository.jpa.AiResponseRepository;
import com.pravoos.ai.document.api.DocumentAccess;
import com.pravoos.ai.shared.arbitr.ArbitrCaseData;
import com.pravoos.ai.shared.arbitr.ArbitrCaseProvider;
import com.pravoos.ai.shared.config.CitationCheckProperties;
import com.pravoos.ai.shared.exception.AiResponseNotFoundException;
import com.pravoos.ai.shared.model.enums.CitationStatus;
import com.pravoos.ai.shared.model.enums.CitationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CitationCheckServiceTest {

    @Mock private ArbitrCaseProvider arbitrCaseProvider;
    @Mock private DocumentAccess documentAccess;
    @Mock private AiResponseRepository aiResponseRepository;

    private CitationCheckService service;

    @BeforeEach
    void setUp() {
        CitationExtractor extractor = new CitationExtractor(new LegalActRegistry());
        service = new CitationCheckService(extractor, arbitrCaseProvider, documentAccess,
                aiResponseRepository, new CitationCheckProperties(100, 25));
    }

    @Test
    void verifiesCourtCaseFoundInArbitr() {
        when(arbitrCaseProvider.isEnabled()).thenReturn(true);
        when(arbitrCaseProvider.fetchCase("А40-12345/2024"))
                .thenReturn(Optional.of(new ArbitrCaseData("А40-12345/2024", "guid", null, List.of())));

        CitationCheckResult result = service.check("Дело А40-12345/2024.", UUID.randomUUID());

        assertThat(result.citations())
                .filteredOn(c -> c.type() == CitationType.COURT_CASE)
                .singleElement()
                .satisfies(c -> assertThat(c.status()).isEqualTo(CitationStatus.VERIFIED));
    }

    @Test
    void flagsCourtCaseNotFound() {
        when(arbitrCaseProvider.isEnabled()).thenReturn(true);
        when(arbitrCaseProvider.fetchCase(anyString())).thenReturn(Optional.empty());

        CitationCheckResult result = service.check("Дело А40-99999/2024.", UUID.randomUUID());

        assertThat(result.notFound()).isEqualTo(1);
        assertThat(result.citations().get(0).status()).isEqualTo(CitationStatus.NOT_FOUND);
    }

    @Test
    void marksCourtCaseUnverifiedWhenArbitrDisabled() {
        when(arbitrCaseProvider.isEnabled()).thenReturn(false);

        CitationCheckResult result = service.check("Дело А40-1/2024.", UUID.randomUUID());

        assertThat(result.unverified()).isEqualTo(1);
        assertThat(result.citations().get(0).status()).isEqualTo(CitationStatus.UNVERIFIED);
    }

    @Test
    void verifiesStatuteGroundedInKnowledgeBase() {
        when(documentAccess.knowledgeBaseMentions(anyString())).thenReturn(true);

        CitationCheckResult result = service.check("Согласно ст. 61.2 Закона о банкротстве.", UUID.randomUUID());

        assertThat(result.citations())
                .filteredOn(c -> c.type() == CitationType.STATUTE)
                .singleElement()
                .satisfies(c -> assertThat(c.status()).isEqualTo(CitationStatus.VERIFIED));
    }

    @Test
    void marksStatuteUnverifiedWhenNotGrounded() {
        when(documentAccess.knowledgeBaseMentions(anyString())).thenReturn(false);

        CitationCheckResult result = service.check("Нарушена ст. 42 некоего акта.", UUID.randomUUID());

        assertThat(result.citations())
                .filteredOn(c -> c.type() == CitationType.STATUTE)
                .singleElement()
                .satisfies(c -> assertThat(c.status()).isEqualTo(CitationStatus.UNVERIFIED));
    }

    @Test
    void checkResponseRejectsForeignOwner() {
        UUID responseId = UUID.randomUUID();
        AiResponse response = mock(AiResponse.class);
        lenient().when(response.getLawyerId()).thenReturn(UUID.randomUUID());
        when(aiResponseRepository.findById(responseId)).thenReturn(Optional.of(response));

        assertThatThrownBy(() -> service.checkResponse(responseId, UUID.randomUUID()))
                .isInstanceOf(AiResponseNotFoundException.class);
    }
}
