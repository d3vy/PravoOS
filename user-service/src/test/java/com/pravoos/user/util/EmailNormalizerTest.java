package com.pravoos.user.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EmailNormalizerTest {

    @Test
    void trimsAndLowercases() {
        assertThat(EmailNormalizer.normalize("  Ivan.Petrov@Example.COM ")).isEqualTo("ivan.petrov@example.com");
    }

    @Test
    void nullBecomesEmptyString() {
        assertThat(EmailNormalizer.normalize(null)).isEmpty();
    }
}
