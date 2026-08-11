package com.pravoos.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class UtcTimestampModuleTest {

  private final ObjectMapper objectMapper =
      new ObjectMapper().registerModule(new UtcTimestampModule());

  @Test
  void serializesLocalDateTimeAsExplicitUtcInstant() throws Exception {
    String json = objectMapper.writeValueAsString(LocalDateTime.of(2026, 8, 11, 7, 30, 15));

    assertThat(json).isEqualTo("\"2026-08-11T07:30:15Z\"");
  }

  @Test
  void keepsSecondsForWholeMinutes() throws Exception {
    String json = objectMapper.writeValueAsString(LocalDateTime.of(2026, 8, 11, 7, 30));

    assertThat(json).isEqualTo("\"2026-08-11T07:30:00Z\"");
  }

  @Test
  void readsBackItsOwnOutput() throws Exception {
    LocalDateTime value = LocalDateTime.of(2026, 8, 11, 7, 30, 15);

    String json = objectMapper.writeValueAsString(value);

    assertThat(objectMapper.readValue(json, LocalDateTime.class)).isEqualTo(value);
  }

  @Test
  void convertsIncomingOffsetToUtc() throws Exception {
    LocalDateTime parsed =
        objectMapper.readValue("\"2026-08-11T10:30:15+03:00\"", LocalDateTime.class);

    assertThat(parsed).isEqualTo(LocalDateTime.of(2026, 8, 11, 7, 30, 15));
  }

  @Test
  void stillAcceptsOffsetLessInput() throws Exception {
    LocalDateTime parsed = objectMapper.readValue("\"2026-08-11T07:30:15\"", LocalDateTime.class);

    assertThat(parsed).isEqualTo(LocalDateTime.of(2026, 8, 11, 7, 30, 15));
  }
}
