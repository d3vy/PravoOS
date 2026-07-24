package com.pravoos.observability.logging;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PiiScrubberTest {

  private PiiScrubber scrubber;

  @BeforeEach
  void setup() {
    scrubber = new PiiScrubber();
  }

  @Test
  void masksEmailKeepingDomain() {
    String scrubbed = scrubber.scrub("Юрист iliachuvikin@gmail.com не найден");

    assertThat(scrubbed).isEqualTo("Юрист il***@gmail.com не найден");
  }

  @Test
  void redactsJwt() {
    String jwt = "eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiIxMjMifQ.c2lnbmF0dXJlLXZhbHVl";

    assertThat(scrubber.scrub("token=" + jwt)).isEqualTo("token=[redacted]");
  }

  @Test
  void redactsBearerHeaderValue() {
    String scrubbed = scrubber.scrub("Authorization: Bearer abcdef0123456789");

    assertThat(scrubbed).isEqualTo("Authorization: Bearer [redacted]");
  }

  @Test
  void redactsRussianPhone() {
    assertThat(scrubber.scrub("звонить +7 999 123-45-67")).isEqualTo("звонить [redacted]");
    assertThat(scrubber.scrub("звонить 89991234567")).isEqualTo("звонить [redacted]");
  }

  @Test
  void keepsTechnicalTextIntact() {
    String message = "Case 3f2a1b8c-0000-4c1a-9f1e-0a0b0c0d0e0f not visible for lawyer, status=404";

    assertThat(scrubber.scrub(message)).isEqualTo(message);
  }

  @Test
  void passesThroughNullAndBlank() {
    assertThat(scrubber.scrub(null)).isNull();
    assertThat(scrubber.scrub("   ")).isEqualTo("   ");
  }
}
