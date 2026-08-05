package com.pravoos.ai.shared.mail;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class EmailAddressesTest {

  @Test
  void extractsAddressesFromDisplayNameHeaders() {
    assertThat(
            EmailAddresses.extractAll(
                "Иван Петров <Ivan.Petrov@Company.ru>", "secretary@company.ru, boss@company.ru"))
        .containsExactly("ivan.petrov@company.ru", "secretary@company.ru", "boss@company.ru");
  }

  @Test
  void ignoresBlankAndMalformedValues() {
    assertThat(EmailAddresses.extractAll(null, "", "  ", "без адреса")).isEmpty();
    assertThat(EmailAddresses.extractFirst("no-address-here")).isEmpty();
  }

  @Test
  void normalizesToLowerCase() {
    assertThat(EmailAddresses.normalize("  Lawyer@PravoOS.ru ")).isEqualTo("lawyer@pravoos.ru");
    assertThat(EmailAddresses.normalize(null)).isNull();
  }
}
