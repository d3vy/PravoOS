package com.pravoos.ai.core.internal.repository.jpa;

import com.pravoos.ai.core.internal.model.entity.AiTrustCounter;
import com.pravoos.ai.core.internal.model.entity.AiTrustCounter.AiTrustCounterId;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AiTrustCounterRepository extends JpaRepository<AiTrustCounter, AiTrustCounterId> {

  @Modifying
  @Query(
      value =
          """
          INSERT INTO ai_trust_counters (stat_date, metric, value)
          VALUES (:statDate, :metric, :delta)
          ON CONFLICT (stat_date, metric)
          DO UPDATE SET value = ai_trust_counters.value + EXCLUDED.value
          """,
      nativeQuery = true)
  void addDelta(
      @Param("statDate") LocalDate statDate,
      @Param("metric") String metric,
      @Param("delta") long delta);

  @Query(
      value = "SELECT metric, SUM(value) FROM ai_trust_counters GROUP BY metric",
      nativeQuery = true)
  List<Object[]> aggregateByMetric();
}
