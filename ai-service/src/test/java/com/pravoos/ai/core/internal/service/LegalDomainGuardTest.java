package com.pravoos.ai.core.internal.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pravoos.ai.llm.api.LlmClient;
import com.pravoos.ai.llm.api.LlmResult;
import com.pravoos.ai.llm.api.LlmUsage;
import com.pravoos.ai.shared.exception.LlmException;
import com.pravoos.ai.shared.exception.NonLegalQueryException;
import com.pravoos.ai.shared.model.enums.TrustMetric;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LegalDomainGuardTest {

  private LlmClient llmClient;
  private TrustMetricsRecorder trustMetricsRecorder;
  private LegalDomainGuard failOpenGuard;
  private LegalDomainGuard failClosedGuard;

  @BeforeEach
  void setUp() {
    llmClient = mock(LlmClient.class);
    trustMetricsRecorder = mock(TrustMetricsRecorder.class);
    failOpenGuard =
        new LegalDomainGuard(llmClient, trustMetricsRecorder, true, new SimpleMeterRegistry());
    failClosedGuard =
        new LegalDomainGuard(llmClient, trustMetricsRecorder, false, new SimpleMeterRegistry());
  }

  @Test
  void allowsMessageClassifiedAsLegal() {
    when(llmClient.complete(anySystemPrompt(), any(), any(), any()))
        .thenReturn(new LlmResult("YES", LlmUsage.EMPTY));

    failOpenGuard.assertLegalQuery("Каков срок исковой давности?");

    verify(trustMetricsRecorder).record(TrustMetric.GUARD_PASS);
  }

  @Test
  void blocksMessageClassifiedAsNonLegal() {
    when(llmClient.complete(anySystemPrompt(), any(), any(), any()))
        .thenReturn(new LlmResult("NO", LlmUsage.EMPTY));

    assertThatThrownBy(() -> failOpenGuard.assertLegalQuery("Какая завтра погода?"))
        .isInstanceOf(NonLegalQueryException.class);

    verify(trustMetricsRecorder).record(TrustMetric.GUARD_BLOCK);
  }

  @Test
  void failsOpenOnLlmExceptionWhenFailOpenEnabled() {
    when(llmClient.complete(anySystemPrompt(), any(), any(), any()))
        .thenThrow(new LlmException("unavailable"));

    failOpenGuard.assertLegalQuery("Вопрос");

    verify(trustMetricsRecorder).record(TrustMetric.GUARD_PASS);
  }

  @Test
  void failsClosedOnLlmExceptionWhenFailOpenDisabled() {
    when(llmClient.complete(anySystemPrompt(), any(), any(), any()))
        .thenThrow(new LlmException("unavailable"));

    assertThatThrownBy(() -> failClosedGuard.assertLegalQuery("Вопрос"))
        .isInstanceOf(NonLegalQueryException.class);

    verify(trustMetricsRecorder).record(TrustMetric.GUARD_BLOCK);
  }

  @Test
  void failsOpenOnEmptyLlmResponse() {
    when(llmClient.complete(anySystemPrompt(), any(), any(), any())).thenReturn(null);

    failOpenGuard.assertLegalQuery("Вопрос");

    verify(trustMetricsRecorder).record(TrustMetric.GUARD_PASS);
  }

  @Test
  void failsOpenOnNullResultContent() {
    when(llmClient.complete(anySystemPrompt(), any(), any(), any()))
        .thenReturn(new LlmResult(null, LlmUsage.EMPTY));

    failOpenGuard.assertLegalQuery("Вопрос");

    verify(trustMetricsRecorder).record(TrustMetric.GUARD_PASS);
  }

  @Test
  void cachesVerdictAndSkipsLlmOnSecondCallForSameNormalizedMessage() {
    when(llmClient.complete(anySystemPrompt(), any(), any(), any()))
        .thenReturn(new LlmResult("YES", LlmUsage.EMPTY));

    failOpenGuard.assertLegalQuery("  Вопрос   про   право  ");
    failOpenGuard.assertLegalQuery("вопрос про право");

    verify(llmClient, times(1)).complete(anySystemPrompt(), any(), any(), any());
    verify(trustMetricsRecorder, times(2)).record(TrustMetric.GUARD_PASS);
  }

  @Test
  void unclassifiedVerdictWordIsTreatedAsNonLegal() {
    when(llmClient.complete(anySystemPrompt(), any(), any(), any()))
        .thenReturn(new LlmResult("MAYBE", LlmUsage.EMPTY));

    assertThatThrownBy(() -> failOpenGuard.assertLegalQuery("Вопрос"))
        .isInstanceOf(NonLegalQueryException.class);
  }

  private String anySystemPrompt() {
    return org.mockito.ArgumentMatchers.anyString();
  }

  private <T> T any() {
    return org.mockito.ArgumentMatchers.any();
  }
}
