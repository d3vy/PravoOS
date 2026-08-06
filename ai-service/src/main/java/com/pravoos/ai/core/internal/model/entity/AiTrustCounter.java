package com.pravoos.ai.core.internal.model.entity;

import com.pravoos.ai.shared.model.enums.TrustMetric;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;

@Entity
@Table(name = "ai_trust_counters")
public class AiTrustCounter {

  @EmbeddedId private AiTrustCounterId id;

  @Column(nullable = false)
  private long value;

  protected AiTrustCounter() {}

  public AiTrustCounterId getId() {
    return id;
  }

  public long getValue() {
    return value;
  }

  @Embeddable
  public static class AiTrustCounterId implements Serializable {

    @Column(name = "stat_date", nullable = false)
    private LocalDate statDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "metric", nullable = false, length = 40)
    private TrustMetric metric;

    protected AiTrustCounterId() {}

    public LocalDate getStatDate() {
      return statDate;
    }

    public TrustMetric getMetric() {
      return metric;
    }

    @Override
    public boolean equals(Object other) {
      if (this == other) {
        return true;
      }
      if (!(other instanceof AiTrustCounterId that)) {
        return false;
      }
      return Objects.equals(statDate, that.statDate) && metric == that.metric;
    }

    @Override
    public int hashCode() {
      return Objects.hash(statDate, metric);
    }
  }
}
