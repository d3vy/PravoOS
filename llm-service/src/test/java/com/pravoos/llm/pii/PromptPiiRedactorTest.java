package com.pravoos.llm.pii;

import static org.assertj.core.api.Assertions.assertThat;

import com.pravoos.llm.config.PiiRedactionProperties;
import com.pravoos.llm.domain.LlmMessage;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import org.junit.jupiter.api.Test;

class PromptPiiRedactorTest {

  private final PromptPiiRedactor redactor =
      new PromptPiiRedactor(
          new PiiRedactionProperties(true, true, null), new SimpleMeterRegistry());

  @Test
  void masksEmailPhoneAndInn() {
    RedactionSession session = redactor.newSession();

    String redacted =
        redactor.redact(
            "Связаться: ivan.petrov@example.com, тел. +7 (999) 123-45-67, ИНН 7707083893", session);

    assertThat(redacted).doesNotContain("ivan.petrov@example.com", "7707083893");
    assertThat(redacted).doesNotContain("999");
    assertThat(redacted).contains("[EMAIL_1]", "[PHONE_1]", "[INN_1]");
  }

  @Test
  void masksRussianFullNameAndInitials() {
    RedactionSession session = redactor.newSession();

    String redacted = redactor.redact("Истец Иванов Иван Иванович, ответчик Петров С. С.", session);

    assertThat(redacted).doesNotContain("Иванов Иван Иванович", "Петров С. С.");
    assertThat(redacted).contains("[NAME_1]", "[NAME_2]");
  }

  @Test
  void reusesSamePlaceholderForRepeatedValueAcrossMessages() {
    RedactionSession session = redactor.newSession();

    String system = redactor.redact("Клиент: petrov@mail.ru", session);
    List<LlmMessage> history =
        redactor.redact(List.of(new LlmMessage("user", "Написать на petrov@mail.ru")), session);

    assertThat(system).contains("[EMAIL_1]");
    assertThat(history.get(0).content()).contains("[EMAIL_1]");
    assertThat(session.size()).isEqualTo(1);
  }

  @Test
  void restoreReturnsOriginalValuesInModelAnswer() {
    RedactionSession session = redactor.newSession();
    redactor.redact("Иванов Иван Иванович, ivan@example.com", session);

    String restored = session.restore("Направьте письмо [NAME_1] по адресу [EMAIL_1].");

    assertThat(restored)
        .isEqualTo("Направьте письмо Иванов Иван Иванович по адресу ivan@example.com.");
  }

  @Test
  void leavesTextIntactWhenNothingMatches() {
    RedactionSession session = redactor.newSession();

    String redacted = redactor.redact("Составить договор поставки на условиях предоплаты", session);

    assertThat(redacted).isEqualTo("Составить договор поставки на условиях предоплаты");
    assertThat(session.isEmpty()).isTrue();
  }

  @Test
  void disabledRedactorPassesTextThrough() {
    PromptPiiRedactor disabled =
        new PromptPiiRedactor(
            new PiiRedactionProperties(false, false, null), new SimpleMeterRegistry());
    RedactionSession session = disabled.newSession();

    assertThat(disabled.redact("ivan@example.com", session)).isEqualTo("ivan@example.com");
  }

  @Test
  void categoryFilterLimitsWhatIsMasked() {
    PromptPiiRedactor emailOnly =
        new PromptPiiRedactor(
            new PiiRedactionProperties(true, true, "EMAIL"), new SimpleMeterRegistry());
    RedactionSession session = emailOnly.newSession();

    String redacted = emailOnly.redact("ivan@example.com, ИНН 7707083893", session);

    assertThat(redacted).contains("[EMAIL_1]", "7707083893");
  }

  @Test
  void embeddingRedactionCanBeDisabledSeparately() {
    PromptPiiRedactor promptOnly =
        new PromptPiiRedactor(
            new PiiRedactionProperties(true, false, null), new SimpleMeterRegistry());
    RedactionSession session = promptOnly.newSession();

    assertThat(promptOnly.redactForEmbedding("ivan@example.com", session))
        .isEqualTo("ivan@example.com");
    assertThat(promptOnly.redact("ivan@example.com", session)).isEqualTo("[EMAIL_1]");
  }
}
