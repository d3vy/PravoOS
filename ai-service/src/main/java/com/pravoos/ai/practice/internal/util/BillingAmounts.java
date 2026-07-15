package com.pravoos.ai.practice.internal.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class BillingAmounts {

    private static final BigDecimal MINUTES_PER_HOUR = BigDecimal.valueOf(60);
    private static final int MONEY_SCALE = 2;

    private BillingAmounts() {
    }

    public static BigDecimal lineAmount(int minutes, BigDecimal hourlyRate) {
        if (hourlyRate == null || minutes <= 0) {
            return BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        }
        return hourlyRate
                .multiply(BigDecimal.valueOf(minutes))
                .divide(MINUTES_PER_HOUR, MONEY_SCALE, RoundingMode.HALF_UP);
    }

    public static BigDecimal normalize(BigDecimal amount) {
        BigDecimal value = amount == null ? BigDecimal.ZERO : amount;
        return value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }
}
