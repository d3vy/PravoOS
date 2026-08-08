package com.pravoos.ai.core.internal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pravoos.ai.core.api.AiResponseDto;
import com.pravoos.ai.core.api.CaseAccessProvider;
import com.pravoos.ai.core.internal.dto.RateRequest;
import com.pravoos.ai.core.internal.model.entity.AiResponse;
import com.pravoos.ai.core.internal.repository.jpa.AiResponseRepository;
import com.pravoos.ai.shared.exception.AiResponseNotFoundException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AiResponseServiceTest {

  @Mock private AiResponseRepository aiResponseRepository;
  @Mock private CaseAccessProvider caseAccessProvider;

  private AiResponseService service;

  private final UUID lawyerId = UUID.randomUUID();
  private final UUID caseId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    service = new AiResponseService(aiResponseRepository, caseAccessProvider);
  }

  private AiResponse response() {
    AiResponse response = new AiResponse();
    ReflectionTestUtils.setField(response, "id", UUID.randomUUID());
    response.setCaseId(caseId);
    response.setLawyerId(lawyerId);
    response.setWorkflowId("CLAIM_DRAFTING");
    response.setQuery("query");
    response.setResult("result");
    response.setSources(List.of());
    return response;
  }

  @Test
  void findByCaseChecksVisibilityAndMapsResults() {
    when(aiResponseRepository.findByCaseIdOrderByCreatedAtDesc(caseId))
        .thenReturn(List.of(response()));

    List<AiResponseDto> result = service.findByCase(caseId, lawyerId, List.of());

    assertThat(result).hasSize(1);
    verify(caseAccessProvider).assertCaseVisible(caseId, lawyerId, List.of());
  }

  @Test
  void findByCaseReturnsEmptyListWhenNoResponses() {
    when(aiResponseRepository.findByCaseIdOrderByCreatedAtDesc(caseId)).thenReturn(List.of());

    assertThat(service.findByCase(caseId, lawyerId, List.of())).isEmpty();
  }

  @Test
  void rateThrowsWhenResponseMissing() {
    UUID responseId = UUID.randomUUID();
    when(aiResponseRepository.findById(responseId)).thenReturn(Optional.empty());

    assertThatThrownBy(
            () -> service.rate(responseId, new RateRequest(1, null), lawyerId, List.of()))
        .isInstanceOf(AiResponseNotFoundException.class);
  }

  @Test
  void rateChecksCaseVisibilityBeforeSaving() {
    AiResponse response = response();
    when(aiResponseRepository.findById(response.getId())).thenReturn(Optional.of(response));
    org.mockito.Mockito.doThrow(new RuntimeException("not visible"))
        .when(caseAccessProvider)
        .assertCaseVisible(caseId, lawyerId, List.of());

    assertThatThrownBy(
            () -> service.rate(response.getId(), new RateRequest(1, null), lawyerId, List.of()))
        .isInstanceOf(RuntimeException.class);
    verify(aiResponseRepository, never()).save(any());
  }

  @Test
  void rateUpdatesRatingAndComment() {
    AiResponse response = response();
    when(aiResponseRepository.findById(response.getId())).thenReturn(Optional.of(response));
    when(aiResponseRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    AiResponseDto result =
        service.rate(response.getId(), new RateRequest(-1, "плохо"), lawyerId, List.of());

    assertThat(result.rating()).isEqualTo((short) -1);
    assertThat(response.getRatingComment()).isEqualTo("плохо");
    verify(aiResponseRepository).save(eq(response));
  }
}
