package com.pravoos.ai.core.internal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.pravoos.ai.core.internal.dto.CitationCheckResult;
import com.pravoos.ai.core.internal.model.entity.AiResponse;
import com.pravoos.ai.core.internal.repository.jpa.AiResponseRepository;
import com.pravoos.ai.court.api.CourtCaseData;
import com.pravoos.ai.court.api.CourtCaseLookup;
import com.pravoos.ai.document.api.DocumentAccess;
import com.pravoos.ai.document.api.LegislationRef;
import com.pravoos.ai.shared.config.CitationCheckProperties;
import com.pravoos.ai.shared.exception.AiResponseNotFoundException;
import com.pravoos.ai.shared.model.enums.CitationStatus;
import com.pravoos.ai.shared.model.enums.CitationType;
import com.pravoos.ai.shared.model.enums.CourtSystem;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CitationCheckServiceTest {

  @Mock private CourtCaseLookup courtCaseLookup;
  @Mock private DocumentAccess documentAccess;
  @Mock private AiResponseRepository aiResponseRepository;
  @Mock private TrustMetricsRecorder trustMetricsRecorder;

  private CitationCheckService service;

  @BeforeEach
  void setUp() {
    CitationExtractor extractor = new CitationExtractor(new LegalActRegistry());
    service =
        new CitationCheckService(
            extractor,
            courtCaseLookup,
            documentAccess,
            aiResponseRepository,
            new CitationCheckProperties(100, 25),
            trustMetricsRecorder,
            new io.micrometer.core.instrument.simple.SimpleMeterRegistry());
  }

  @Test
  void verifiesCourtCaseFoundInArbitr() {
    when(courtCaseLookup.isEnabled(CourtSystem.ARBITR)).thenReturn(true);
    when(courtCaseLookup.fetchCase(CourtSystem.ARBITR, "А40-12345/2024"))
        .thenReturn(
            Optional.of(
                new CourtCaseData("А40-12345/2024", "guid", null, null, List.of(), List.of())));

    CitationCheckResult result = service.check("Дело А40-12345/2024.", UUID.randomUUID());

    assertThat(result.citations())
        .filteredOn(c -> c.type() == CitationType.COURT_CASE)
        .singleElement()
        .satisfies(c -> assertThat(c.status()).isEqualTo(CitationStatus.VERIFIED));
  }

  @Test
  void flagsCourtCaseNotFound() {
    when(courtCaseLookup.isEnabled(CourtSystem.ARBITR)).thenReturn(true);
    when(courtCaseLookup.fetchCase(any(), anyString())).thenReturn(Optional.empty());

    CitationCheckResult result = service.check("Дело А40-99999/2024.", UUID.randomUUID());

    assertThat(result.notFound()).isEqualTo(1);
    assertThat(result.citations().get(0).status()).isEqualTo(CitationStatus.NOT_FOUND);
  }

  @Test
  void marksCourtCaseUnverifiedWhenArbitrDisabled() {
    when(courtCaseLookup.isEnabled(CourtSystem.ARBITR)).thenReturn(false);

    CitationCheckResult result = service.check("Дело А40-1/2024.", UUID.randomUUID());

    assertThat(result.unverified()).isEqualTo(1);
    assertThat(result.citations().get(0).status()).isEqualTo(CitationStatus.UNVERIFIED);
  }

  @Test
  void verifiesStatuteBackedByCurrentLegislation() {
    when(documentAccess.currentLegislation(eq("61.2"), any()))
        .thenReturn(
            Optional.of(
                new LegislationRef("Закон о банкротстве", "61.2", LocalDate.of(2024, 8, 8))));

    CitationCheckResult result =
        service.check("Согласно ст. 61.2 Закона о банкротстве.", UUID.randomUUID());

    assertThat(result.citations())
        .filteredOn(c -> c.type() == CitationType.STATUTE)
        .singleElement()
        .satisfies(
            c -> {
              assertThat(c.status()).isEqualTo(CitationStatus.VERIFIED);
              assertThat(c.normalized()).contains("ред. от 08.08.2024");
            });
  }

  @Test
  void marksStatuteOutdatedWhenOnlySupersededEditionIsLoaded() {
    when(documentAccess.currentLegislation(anyString(), any())).thenReturn(Optional.empty());
    when(documentAccess.supersededLegislation(eq("61.2"), any()))
        .thenReturn(
            Optional.of(
                new LegislationRef("Закон о банкротстве", "61.2", LocalDate.of(2020, 1, 15))));

    CitationCheckResult result =
        service.check("Согласно ст. 61.2 Закона о банкротстве.", UUID.randomUUID());

    assertThat(result.outdated()).isEqualTo(1);
    assertThat(result.citations())
        .filteredOn(c -> c.type() == CitationType.STATUTE)
        .singleElement()
        .satisfies(
            c -> {
              assertThat(c.status()).isEqualTo(CitationStatus.OUTDATED);
              assertThat(c.detail()).contains("15.01.2020");
            });
  }

  @Test
  void marksStatuteUnverifiedWhenOnlyMentionedButNotCurrentLegislation() {
    when(documentAccess.currentLegislation(anyString(), any())).thenReturn(Optional.empty());
    when(documentAccess.knowledgeBaseMentions(anyString())).thenReturn(true);

    CitationCheckResult result =
        service.check("Согласно ст. 61.2 Закона о банкротстве.", UUID.randomUUID());

    assertThat(result.citations())
        .filteredOn(c -> c.type() == CitationType.STATUTE)
        .singleElement()
        .satisfies(c -> assertThat(c.status()).isEqualTo(CitationStatus.UNVERIFIED));
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
