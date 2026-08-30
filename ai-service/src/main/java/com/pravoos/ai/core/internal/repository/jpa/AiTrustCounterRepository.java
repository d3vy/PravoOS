package com.pravoos.ai.core.internal.repository.jpa;

import com.pravoos.ai.core.internal.model.entity.AiTrustCounter;
import com.pravoos.ai.core.internal.model.entity.AiTrustCounter.AiTrustCounterId;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AiTrustCounterRepository extends JpaRepository<AiTrustCounter, AiTrustCounterId> {

  @Modifying
  @Query(
      value =
          """
          WITH new_attempt AS (
              INSERT INTO ai_trust_counter_flush_attempts (attempt_id, stat_date, metric, delta)
              VALUES (:attemptId, :statDate, :metric, :delta)
              ON CONFLICT (attempt_id) DO NOTHING
              RETURNING delta
          )
          INSERT INTO ai_trust_counters (stat_date, metric, value)
          SELECT :statDate, :metric, delta FROM new_attempt
          ON CONFLICT (stat_date, metric)
          DO UPDATE SET value = ai_trust_counters.value + EXCLUDED.value
          """,
      nativeQuery = true)
  void addDelta(
      @Param("attemptId") UUID attemptId,
      @Param("statDate") LocalDate statDate,
      @Param("metric") String metric,
      @Param("delta") long delta);

  @Query(
      value = "SELECT metric, SUM(value) FROM ai_trust_counters GROUP BY metric",
      nativeQuery = true)
  List<Object[]> aggregateByMetric();
}
