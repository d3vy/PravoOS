package com.pravoos.ai.document.internal.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class ClamdVersionParserTest {

  @Test
  void parsesSignatureDate() {
    assertThat(ClamdVersionParser.signatureDate("ClamAV 1.4.2/27649/Mon Jul 21 09:16:30 2025"))
        .contains(LocalDate.of(2025, 7, 21));
  }

  @Test
  void parsesSpacePaddedDayOfMonth() {
    assertThat(ClamdVersionParser.signatureDate("ClamAV 1.4.2/27600/Mon Jul  7 09:16:30 2025"))
        .contains(LocalDate.of(2025, 7, 7));
  }

  @Test
  void returnsEmptyWhenDatabaseNotLoaded() {
    assertThat(ClamdVersionParser.signatureDate("ClamAV 1.4.2")).isEmpty();
  }

  @Test
  void returnsEmptyOnUnparsableTimestamp() {
    assertThat(ClamdVersionParser.signatureDate("ClamAV 1.4.2/27649/вчера")).isEmpty();
  }

  @Test
  void returnsEmptyOnNullResponse() {
    assertThat(ClamdVersionParser.signatureDate(null)).isEmpty();
  }
}
