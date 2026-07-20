package com.pravoos.user.shared.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EmailMaskerTest {

    @Test
    void masksLocalPartKeepingFirstCharAndDomain() {
        assertThat(EmailMasker.mask("ivan.petrov@example.com")).isEqualTo("i***@example.com");
    }

    @Test
    void returnsPlaceholderForNullOrBlank() {
        assertThat(EmailMasker.mask(null)).isEqualTo("***");
        assertThat(EmailMasker.mask("   ")).isEqualTo("***");
    }

    @Test
    void returnsPlaceholderWhenNoAtSymbol() {
        assertThat(EmailMasker.mask("not-an-email")).isEqualTo("***");
    }

    @Test
    void returnsPlaceholderWhenLocalPartEmpty() {
        assertThat(EmailMasker.mask("@example.com")).isEqualTo("***");
    }
}
