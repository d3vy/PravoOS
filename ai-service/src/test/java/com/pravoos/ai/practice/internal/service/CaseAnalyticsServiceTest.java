package com.pravoos.ai.practice.internal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pravoos.ai.core.api.LegalAiAnswer;
import com.pravoos.ai.core.api.LegalAiPort;
import com.pravoos.ai.practice.internal.dto.CaseAnalyticsResponse;
import com.pravoos.ai.practice.internal.dto.OutcomeStat;
import com.pravoos.ai.practice.internal.model.entity.Case;
import com.pravoos.ai.practice.internal.model.entity.CaseAnalysis;
import com.pravoos.ai.practice.internal.model.entity.CaseHearingEvent;
import com.pravoos.ai.practice.internal.model.entity.CaseParty;
import com.pravoos.ai.practice.internal.repository.jpa.CaseAnalysisRepository;
import com.pravoos.ai.practice.internal.repository.jpa.CaseHearingEventRepository;
import com.pravoos.ai.practice.internal.repository.jpa.CasePartyRepository;
import com.pravoos.ai.practice.internal.repository.jpa.CaseRepository;
import com.pravoos.ai.shared.model.enums.CaseStatus;
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
class CaseAnalyticsServiceTest {

  @Mock private CaseService caseService;
  @Mock private CaseRepository caseRepository;
  @Mock private CaseHearingEventRepository hearingEventRepository;
  @Mock private CasePartyRepository casePartyRepository;
  @Mock private CaseAnalysisRepository caseAnalysisRepository;
  @Mock private LegalAiPort legalAiPort;

  private final UUID caseId = UUID.randomUUID();
  private final UUID lawyerId = UUID.randomUUID();

  private CaseAnalyticsService service;

  @BeforeEach
  void setUp() {
    CaseAnalyticsService self =
        new CaseAnalyticsService(
            caseService,
            caseRepository,
            hearingEventRepository,
            casePartyRepository,
            caseAnalysisRepository,
            legalAiPort,
            null);
    service =
        new CaseAnalyticsService(
            caseService,
            caseRepository,
            hearingEventRepository,
            casePartyRepository,
            caseAnalysisRepository,
            legalAiPort,
            self);
    lenient()
        .when(caseService.requireVisibleCase(eq(caseId), eq(lawyerId), anyList()))
        .thenReturn(caseEntity(null));
    lenient().when(casePartyRepository.findByCaseId(caseId)).thenReturn(List.of());
    lenient().when(caseAnalysisRepository.findByCaseId(caseId)).thenReturn(Optional.empty());
    lenient()
        .when(
            caseRepository.courtStatistics(
                anyList(),
                eq(lawyerId),
                anyList(),
                eq(CaseStatus.CLOSED_WON),
                eq(CaseStatus.CLOSED_LOST)))
        .thenReturn(List.of());
  }

  private Case caseEntity(String judge) {
    Case caseEntity = new Case();
    caseEntity.setLawyerId(lawyerId);
    caseEntity.setTitle("Взыскание задолженности");
    caseEntity.setStatus(CaseStatus.IN_PROGRESS);
    caseEntity.setNextHearingDate(LocalDate.of(2026, 8, 1));
    caseEntity.setJudgeName(judge);
    return caseEntity;
  }

  private CaseHearingEvent event(LocalDate date, String type, String court) {
    return new CaseHearingEvent(
        caseId, UUID.randomUUID().toString(), date, type, "описание", court);
  }

  @Test
  void timelineStatsAggregateEventsAndCourts() {
    when(hearingEventRepository.findByCaseIdOrderByEventDateDescCreatedAtDesc(caseId))
        .thenReturn(
            List.of(
                event(LocalDate.of(2026, 6, 10), "Заседание", "АС города Москвы"),
                event(LocalDate.of(2026, 5, 1), "Заседание", "АС города Москвы"),
                event(LocalDate.of(2026, 4, 1), "Определение", "АС города Москвы")));

    CaseAnalyticsResponse response = service.getAnalytics(caseId, lawyerId, List.of());

    assertThat(response.timeline().hearingCount()).isEqualTo(3);
    assertThat(response.timeline().firstEventDate()).isEqualTo(LocalDate.of(2026, 4, 1));
    assertThat(response.timeline().lastEventDate()).isEqualTo(LocalDate.of(2026, 6, 10));
    assertThat(response.timeline().spanDays()).isEqualTo(70);
    assertThat(response.timeline().averageIntervalDays()).isEqualTo(35);
    assertThat(response.timeline().courts()).containsExactly("АС города Москвы");
    assertThat(response.timeline().eventTypes())
        .extracting(t -> t.type())
        .containsExactly("Заседание", "Определение");
    assertThat(response.aiAnalysis()).isNull();
  }

  @Test
  void courtWinRateComputedFromClosedCases() {
    when(hearingEventRepository.findByCaseIdOrderByEventDateDescCreatedAtDesc(caseId))
        .thenReturn(List.of(event(LocalDate.of(2026, 6, 10), "Заседание", "АС города Москвы")));
    when(caseRepository.courtStatistics(
            anyList(),
            eq(lawyerId),
            anyList(),
            eq(CaseStatus.CLOSED_WON),
            eq(CaseStatus.CLOSED_LOST)))
        .thenReturn(List.of(view("АС города Москвы", 5, 3, 1)));

    CaseAnalyticsResponse response = service.getAnalytics(caseId, lawyerId, List.of());

    assertThat(response.courtStats()).hasSize(1);
    OutcomeStat stat = response.courtStats().get(0);
    assertThat(stat.totalCases()).isEqualTo(5);
    assertThat(stat.winRatePercent()).isEqualTo(75);
  }

  @Test
  void judgeAndPartyStatsComputedWhenPresent() {
    when(caseService.requireVisibleCase(eq(caseId), eq(lawyerId), anyList()))
        .thenReturn(caseEntity("Иванов И.И."));
    when(hearingEventRepository.findByCaseIdOrderByEventDateDescCreatedAtDesc(caseId))
        .thenReturn(List.of());
    when(casePartyRepository.findByCaseId(caseId))
        .thenReturn(List.of(new CaseParty(caseId, "ООО Ромашка", "Ответчик")));
    when(caseRepository.judgeStatistics(
            eq(List.of("Иванов И.И.")),
            eq(lawyerId),
            anyList(),
            eq(CaseStatus.CLOSED_WON),
            eq(CaseStatus.CLOSED_LOST)))
        .thenReturn(List.of(view("Иванов И.И.", 4, 2, 2)));
    when(caseRepository.partyStatistics(
            eq(List.of("ООО Ромашка")),
            eq(lawyerId),
            anyList(),
            eq(CaseStatus.CLOSED_WON),
            eq(CaseStatus.CLOSED_LOST)))
        .thenReturn(List.of(view("ООО Ромашка", 3, 3, 0)));

    CaseAnalyticsResponse response = service.getAnalytics(caseId, lawyerId, List.of());

    assertThat(response.timeline().judge()).isEqualTo("Иванов И.И.");
    assertThat(response.timeline().parties())
        .extracting(p -> p.name())
        .containsExactly("ООО Ромашка");
    assertThat(response.judgeStats())
        .singleElement()
        .satisfies(stat -> assertThat(stat.winRatePercent()).isEqualTo(50));
    assertThat(response.partyStats())
        .singleElement()
        .satisfies(stat -> assertThat(stat.winRatePercent()).isEqualTo(100));
  }

  @Test
  void generateSavesAnalysisAndReturnsAiContent() {
    when(hearingEventRepository.findByCaseIdOrderByEventDateDescCreatedAtDesc(caseId))
        .thenReturn(List.of(event(LocalDate.of(2026, 6, 10), "Заседание", "АС города Москвы")));
    when(caseAnalysisRepository.save(any(CaseAnalysis.class)))
        .thenAnswer(inv -> inv.getArgument(0));
    when(legalAiPort.analyzeCase(eq(caseId), any(), any(), any(), eq(lawyerId)))
        .thenReturn(new LegalAiAnswer("аналитическая справка", 42));

    CaseAnalyticsResponse response = service.generateAnalysis(caseId, lawyerId, List.of());

    verify(legalAiPort).assertWithinQuota(lawyerId);
    verify(caseAnalysisRepository).save(any(CaseAnalysis.class));
    assertThat(response.aiAnalysis()).isNotNull();
    assertThat(response.aiAnalysis().content()).isEqualTo("аналитическая справка");
  }

  private CaseRepository.OutcomeStatView view(String name, long total, long won, long lost) {
    return new CaseRepository.OutcomeStatView() {
      @Override
      public String getName() {
        return name;
      }

      @Override
      public long getTotalCases() {
        return total;
      }

      @Override
      public long getWonCases() {
        return won;
      }

      @Override
      public long getLostCases() {
        return lost;
      }
    };
  }
}
