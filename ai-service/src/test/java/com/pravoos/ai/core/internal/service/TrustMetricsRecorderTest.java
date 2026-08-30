package com.pravoos.ai.core.internal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.pravoos.ai.core.internal.repository.jpa.AiTrustCounterRepository;
import com.pravoos.ai.shared.model.enums.TrustMetric;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TrustMetricsRecorderTest {

  @Mock private AiTrustCounterRepository trustCounterRepository;
  @Mock private TrustCounterWriter trustCounterWriter;

  private TrustMetricsRecorder recorder;

  @BeforeEach
  void setUp() {
    recorder = new TrustMetricsRecorder(trustCounterRepository, trustCounterWriter);
  }

  @Test
  void flushWritesAccumulatedDeltasOncePerMetric() {
    recorder.record(TrustMetric.GUARD_PASS);
    recorder.record(TrustMetric.GUARD_PASS);
    recorder.record(TrustMetric.GUARD_BLOCK);

    recorder.flush();

    verify(trustCounterWriter)
        .addDelta(any(UUID.class), any(LocalDate.class), eq(TrustMetric.GUARD_PASS), eq(2L));
    verify(trustCounterWriter)
        .addDelta(any(UUID.class), any(LocalDate.class), eq(TrustMetric.GUARD_BLOCK), eq(1L));
  }

  @Test
  void flushWithoutRecordedEventsTouchesNothing() {
    recorder.flush();

    verifyNoInteractions(trustCounterWriter);
  }

  @Test
  void flushDoesNotWriteTheSameDeltaTwice() {
    recorder.record(TrustMetric.CITATION_VERIFIED);
    recorder.flush();
    recorder.flush();

    verify(trustCounterWriter)
        .addDelta(any(UUID.class), any(LocalDate.class), eq(TrustMetric.CITATION_VERIFIED), eq(1L));
  }

  @Test
  void failedFlushKeepsDeltaForTheNextAttempt() {
    doThrow(new IllegalStateException("db down"))
        .when(trustCounterWriter)
        .addDelta(any(UUID.class), any(LocalDate.class), any(TrustMetric.class), anyLong());
    recorder.record(TrustMetric.GUARD_BLOCK);

    recorder.flush();

    assertThat(recorder.totals()).containsEntry(TrustMetric.GUARD_BLOCK, 1L);
  }

  @Test
  void failureOfOneMetricDoesNotDiscardTheOthers() {
    doThrow(new IllegalStateException("db down"))
        .when(trustCounterWriter)
        .addDelta(any(UUID.class), any(LocalDate.class), eq(TrustMetric.GUARD_BLOCK), anyLong());
    recorder.record(TrustMetric.GUARD_BLOCK);
    recorder.record(TrustMetric.GUARD_PASS);

    recorder.flush();

    verify(trustCounterWriter)
        .addDelta(any(UUID.class), any(LocalDate.class), eq(TrustMetric.GUARD_PASS), eq(1L));
    assertThat(recorder.totals())
        .containsEntry(TrustMetric.GUARD_BLOCK, 1L)
        .containsEntry(TrustMetric.GUARD_PASS, 0L);
  }

  @Test
  void totalsSumPersistedRowsAndUnflushedBuffer() {
    List<Object[]> rows = List.<Object[]>of(new Object[] {"GUARD_PASS", 10L});
    when(trustCounterRepository.aggregateByMetric()).thenReturn(rows);
    recorder.record(TrustMetric.GUARD_PASS);

    assertThat(recorder.totals())
        .containsEntry(TrustMetric.GUARD_PASS, 11L)
        .containsEntry(TrustMetric.CITATION_OUTDATED, 0L);
  }
}
