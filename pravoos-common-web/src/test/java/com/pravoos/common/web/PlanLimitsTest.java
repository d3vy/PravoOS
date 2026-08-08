package com.pravoos.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PlanLimitsTest {

  @Test
  void blankOrNullCode_defaultsToUnknown() {
    assertThat(new PlanLimits(null, 10, 100).code()).isEqualTo("UNKNOWN");
    assertThat(new PlanLimits("  ", 10, 100).code()).isEqualTo("UNKNOWN");
  }

  @Test
  void negativeLimits_clampedToZero() {
    PlanLimits limits = new PlanLimits("PRO", -5, -100L);

    assertThat(limits.dailyRequests()).isZero();
    assertThat(limits.dailyTokens()).isZero();
  }

  @Test
  void quotaDisabled_trueWhenBothLimitsNonPositive() {
    assertThat(new PlanLimits("PRO", 0, 0L).quotaDisabled()).isTrue();
  }

  @Test
  void quotaDisabled_falseWhenEitherLimitPositive() {
    assertThat(new PlanLimits("PRO", 10, 0L).quotaDisabled()).isFalse();
    assertThat(new PlanLimits("PRO", 0, 1000L).quotaDisabled()).isFalse();
  }
}
