package com.pravoos.llm.pii;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pravoos.llm.config.PiiRedactionProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class StreamRestorerTest {

  private final PromptPiiRedactor redactor =
      new PromptPiiRedactor(
          new PiiRedactionProperties(true, true, null),
          new SimpleMeterRegistry(),
          new ObjectMapper());

  @Test
  void restoresPlaceholderSplitAcrossTokens() {
    RedactionSession session = redactor.newSession();
    redactor.redact("ivan@example.com", session);
    List<String> emitted = new ArrayList<>();
    StreamRestorer restorer = new StreamRestorer(session, emitted::add);

    restorer.accept("Пишите на ");
    restorer.accept("[EMA");
    restorer.accept("IL_1] сегодня");
    restorer.flush();

    assertThat(String.join("", emitted)).isEqualTo("Пишите на ivan@example.com сегодня");
  }

  @Test
  void passesTokensThroughWhenNothingWasRedacted() {
    RedactionSession session = redactor.newSession();
    List<String> emitted = new ArrayList<>();
    StreamRestorer restorer = new StreamRestorer(session, emitted::add);

    restorer.accept("обычный ");
    restorer.accept("текст");
    restorer.flush();

    assertThat(emitted).containsExactly("обычный ", "текст");
  }

  @Test
  void doesNotHoldTextForeverOnUnclosedBracket() {
    RedactionSession session = redactor.newSession();
    redactor.redact("ivan@example.com", session);
    List<String> emitted = new ArrayList<>();
    StreamRestorer restorer = new StreamRestorer(session, emitted::add);

    restorer.accept("см. пункт [1 договора о поставке товаров народного потребления");

    assertThat(String.join("", emitted)).contains("см. пункт [1 договора");
  }

  @Test
  void flushEmitsTrailingBuffer() {
    RedactionSession session = redactor.newSession();
    redactor.redact("ivan@example.com", session);
    List<String> emitted = new ArrayList<>();
    StreamRestorer restorer = new StreamRestorer(session, emitted::add);

    restorer.accept("хвост [EMAIL_1");
    restorer.flush();

    assertThat(String.join("", emitted)).isEqualTo("хвост [EMAIL_1");
  }
}
