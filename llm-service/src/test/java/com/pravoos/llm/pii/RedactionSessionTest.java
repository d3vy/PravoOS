package com.pravoos.llm.pii;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class RedactionSessionTest {

  @Test
  void isEmptyForNewSession() {
    RedactionSession session = new RedactionSession();

    assertThat(session.isEmpty()).isTrue();
    assertThat(session.size()).isZero();
    assertThat(session.mapping()).isEmpty();
  }

  @Test
  void assignsIncrementingPlaceholdersPerLabel() {
    RedactionSession session = new RedactionSession();

    String first = session.placeholderFor("EMAIL", "a@example.com");
    String second = session.placeholderFor("EMAIL", "b@example.com");

    assertThat(first).isEqualTo("[EMAIL_1]");
    assertThat(second).isEqualTo("[EMAIL_2]");
    assertThat(session.size()).isEqualTo(2);
  }

  @Test
  void reusesPlaceholderForRepeatedValue() {
    RedactionSession session = new RedactionSession();

    String first = session.placeholderFor("EMAIL", "a@example.com");
    String second = session.placeholderFor("EMAIL", "a@example.com");

    assertThat(first).isEqualTo(second);
    assertThat(session.size()).isEqualTo(1);
  }

  @Test
  void countersAreIndependentPerLabel() {
    RedactionSession session = new RedactionSession();

    session.placeholderFor("EMAIL", "a@example.com");
    String namePlaceholder = session.placeholderFor("NAME", "Иванов Иван Иванович");

    assertThat(namePlaceholder).isEqualTo("[NAME_1]");
  }

  @Test
  void mappingReturnsPlaceholderToOriginalValueSnapshot() {
    RedactionSession session = new RedactionSession();
    session.placeholderFor("EMAIL", "a@example.com");

    assertThat(session.mapping())
        .containsExactly(java.util.Map.entry("[EMAIL_1]", "a@example.com"));
    assertThatThrownBy(() -> session.mapping().put("x", "y"))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void restoreReturnsInputUnchangedWhenSessionEmptyOrTextEmpty() {
    RedactionSession session = new RedactionSession();

    assertThat(session.restore(null)).isNull();
    assertThat(session.restore("")).isEmpty();
    assertThat(session.restore("[EMAIL_1] hello")).isEqualTo("[EMAIL_1] hello");
  }

  @Test
  void restoreReplacesAllOccurrencesOfPlaceholder() {
    RedactionSession session = new RedactionSession();
    session.placeholderFor("EMAIL", "a@example.com");

    String restored = session.restore("[EMAIL_1] написал, ответ [EMAIL_1] отправлен");

    assertThat(restored).isEqualTo("a@example.com написал, ответ a@example.com отправлен");
  }
}
