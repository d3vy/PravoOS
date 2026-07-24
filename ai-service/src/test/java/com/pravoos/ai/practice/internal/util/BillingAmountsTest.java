package com.pravoos.ai.practice.internal.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class BillingAmountsTest {

  @Test
  void lineAmount_computesRatePerHourProRated() {
    assertThat(BillingAmounts.lineAmount(90, new BigDecimal("4000")))
        .isEqualByComparingTo("6000.00");
  }

  @Test
  void lineAmount_roundsHalfUpToKopecks() {
    assertThat(BillingAmounts.lineAmount(1, new BigDecimal("100"))).isEqualByComparingTo("1.67");
  }

  @Test
  void lineAmount_isZeroForZeroMinutes() {
    assertThat(BillingAmounts.lineAmount(0, new BigDecimal("5000"))).isEqualByComparingTo("0.00");
  }

  @Test
  void lineAmount_isZeroForNullRate() {
    assertThat(BillingAmounts.lineAmount(60, null)).isEqualByComparingTo("0.00");
  }

  @Test
  void normalize_scalesToTwoDecimals() {
    assertThat(BillingAmounts.normalize(new BigDecimal("10"))).hasToString("10.00");
    assertThat(BillingAmounts.normalize(null)).hasToString("0.00");
  }
}
