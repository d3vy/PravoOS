package com.pravoos.llm.pii;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pravoos.llm.config.PiiRedactionProperties;
import com.pravoos.llm.domain.LlmMessage;
import com.pravoos.llm.domain.LlmToolCall;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import org.junit.jupiter.api.Test;

class PromptPiiRedactorTest {

  private final PromptPiiRedactor redactor =
      new PromptPiiRedactor(
          new PiiRedactionProperties(true, true, null),
          new SimpleMeterRegistry(),
          new ObjectMapper());

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
  void masksPassportInEveryFormatItIsWrittenInRussianDocuments() {
    assertThat(redactOnce("Паспорт 45 09 123456"))
        .contains("[PASSPORT_1]")
        .doesNotContain("123456");
    assertThat(redactOnce("Паспорт 4509 123456")).contains("[PASSPORT_1]").doesNotContain("123456");
    assertThat(redactOnce("Паспорт 4509 № 123456"))
        .contains("[PASSPORT_1]")
        .doesNotContain("123456");
    assertThat(redactOnce("Паспорт 45 09 №123456"))
        .contains("[PASSPORT_1]")
        .doesNotContain("123456");
  }

  @Test
  void masksSnilsWrittenWithoutSeparators() {
    assertThat(redactOnce("СНИЛС 112-233-445 95")).contains("[SNILS_1]").doesNotContain("112-233");
    assertThat(redactOnce("СНИЛС 112 233 445 95")).contains("[SNILS_1]").doesNotContain("112 233");
    assertThat(redactOnce("СНИЛС 11223344595")).contains("[SNILS_1]").doesNotContain("11223344595");
  }

  @Test
  void keepsTenDigitInnRecognizableAsInnAndNotAsPassport() {
    assertThat(redactOnce("ИНН 7707083893")).contains("[INN_1]").doesNotContain("7707083893");
  }

  private String redactOnce(String text) {
    return redactor.redact(text, redactor.newSession());
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
            new PiiRedactionProperties(false, false, null),
            new SimpleMeterRegistry(),
            new ObjectMapper());
    RedactionSession session = disabled.newSession();

    assertThat(disabled.redact("ivan@example.com", session)).isEqualTo("ivan@example.com");
  }

  @Test
  void categoryFilterLimitsWhatIsMasked() {
    PromptPiiRedactor emailOnly =
        new PromptPiiRedactor(
            new PiiRedactionProperties(true, true, "EMAIL"),
            new SimpleMeterRegistry(),
            new ObjectMapper());
    RedactionSession session = emailOnly.newSession();

    String redacted = emailOnly.redact("ivan@example.com, ИНН 7707083893", session);

    assertThat(redacted).contains("[EMAIL_1]", "7707083893");
  }

  @Test
  void embeddingRedactionCanBeDisabledSeparately() {
    PromptPiiRedactor promptOnly =
        new PromptPiiRedactor(
            new PiiRedactionProperties(true, false, null),
            new SimpleMeterRegistry(),
            new ObjectMapper());
    RedactionSession session = promptOnly.newSession();

    assertThat(promptOnly.redactForEmbedding("ivan@example.com", session))
        .isEqualTo("ivan@example.com");
    assertThat(promptOnly.redact("ivan@example.com", session)).isEqualTo("[EMAIL_1]");
  }

  @Test
  void redactsToolResultContentAndKeepsToolCallId() {
    RedactionSession session = redactor.newSession();

    List<LlmMessage> history =
        redactor.redact(List.of(LlmMessage.tool("call_1", "Клиент: petrov@mail.ru")), session);

    assertThat(history.get(0).role()).isEqualTo(LlmMessage.ROLE_TOOL);
    assertThat(history.get(0).toolCallId()).isEqualTo("call_1");
    assertThat(history.get(0).content()).contains("[EMAIL_1]").doesNotContain("petrov@mail.ru");
  }

  @Test
  void redactsToolCallArgumentsPerJsonTextNode() {
    RedactionSession session = redactor.newSession();

    List<LlmMessage> history =
        redactor.redact(
            List.of(
                LlmMessage.assistant(
                    null,
                    List.of(
                        new LlmToolCall(
                            "call_1",
                            "create_client",
                            "{\"email\":\"petrov@mail.ru\",\"tags\":[\"vip\"],\"active\":true}")))),
            session);

    String arguments = history.get(0).toolCalls().get(0).argumentsJson();

    assertThat(arguments).contains("[EMAIL_1]").doesNotContain("petrov@mail.ru");
    assertThat(arguments).contains("\"tags\":[\"vip\"]").contains("\"active\":true");
  }

  @Test
  void sharesOnePlaceholderBetweenPlainTextAndToolCallArguments() {
    RedactionSession session = redactor.newSession();

    redactor.redact("Клиент: petrov@mail.ru", session);
    String arguments = redactor.redactJson("{\"email\":\"petrov@mail.ru\"}", session);

    assertThat(arguments).isEqualTo("{\"email\":\"[EMAIL_1]\"}");
    assertThat(session.size()).isEqualTo(1);
  }

  @Test
  void restoresToolCallArgumentsIntoValidJsonWhenValuesNeedEscaping() throws Exception {
    RedactionSession session = redactor.newSession();
    String original = "ООО \"Ромашка\"\\Филиал, ivan@example.com";
    String redacted = redactor.redactJson("{\"title\":\"" + escape(original) + "\"}", session);

    String restored = redactor.restoreJson(redacted, session);

    assertThat(new ObjectMapper().readTree(restored).get("title").textValue()).isEqualTo(original);
  }

  @Test
  void restoresPlaceholdersNestedInsideArraysAndObjects() {
    RedactionSession session = redactor.newSession();
    redactor.redact("ivan@example.com и petrov@mail.ru", session);

    String restored =
        redactor.restoreJson("{\"to\":[\"[EMAIL_1]\"],\"cc\":{\"first\":\"[EMAIL_2]\"}}", session);

    assertThat(restored)
        .isEqualTo("{\"to\":[\"ivan@example.com\"],\"cc\":{\"first\":\"petrov@mail.ru\"}}");
  }

  @Test
  void passesMalformedToolCallArgumentsThroughUntouched() {
    RedactionSession session = redactor.newSession();
    redactor.redact("petrov@mail.ru", session);

    assertThat(redactor.restoreJson("{\"email\": [EMAIL_1", session))
        .isEqualTo("{\"email\": [EMAIL_1");
    assertThat(redactor.redactJson("не json вовсе", session)).isEqualTo("не json вовсе");
  }

  @Test
  void leavesToolCallArgumentsAloneWhenNothingWasRedacted() {
    RedactionSession session = redactor.newSession();

    assertThat(
            redactor.restoreToolCalls(
                List.of(new LlmToolCall("call_1", "get_case", "{}")), session))
        .containsExactly(new LlmToolCall("call_1", "get_case", "{}"));
  }

  private static String escape(String value) {
    return value.replace("\\", "\\\\").replace("\"", "\\\"");
  }
}
