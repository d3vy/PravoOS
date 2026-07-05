package com.pravoos.ai.shared.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LikePatternTest {

    @Test
    void wrapsLowercasedQueryWithWildcards() {
        assertThat(LikePattern.contains("Иванов")).isEqualTo("%иванов%");
    }

    @Test
    void escapesLikeSpecialCharacters() {
        assertThat(LikePattern.contains("50%_off")).isEqualTo("%50\\%\\_off%");
    }

    @Test
    void escapesBackslashFirstToAvoidDoubleEscaping() {
        assertThat(LikePattern.contains("a\\b")).isEqualTo("%a\\\\b%");
    }
}
