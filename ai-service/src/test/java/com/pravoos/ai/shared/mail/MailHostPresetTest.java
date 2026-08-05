package com.pravoos.ai.shared.mail;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class MailHostPresetTest {

  @Test
  void detectsYandexByDomain() {
    assertThat(MailHostPreset.forEmail("Lawyer@Ya.RU")).contains(MailHostPreset.YANDEX);
  }

  @Test
  void detectsMailRuAliasDomains() {
    assertThat(MailHostPreset.forEmail("lawyer@bk.ru")).contains(MailHostPreset.MAIL_RU);
    assertThat(MailHostPreset.forEmail("lawyer@list.ru")).contains(MailHostPreset.MAIL_RU);
  }

  @Test
  void detectsGmail() {
    assertThat(MailHostPreset.forEmail("lawyer@gmail.com")).contains(MailHostPreset.GMAIL);
  }

  @Test
  void returnsEmptyForUnknownDomain() {
    assertThat(MailHostPreset.forEmail("lawyer@corp-legal.ru")).isEmpty();
  }

  @Test
  void returnsEmptyForMalformedAddress() {
    assertThat(MailHostPreset.forEmail(null)).isEqualTo(Optional.empty());
    assertThat(MailHostPreset.forEmail("  ")).isEmpty();
    assertThat(MailHostPreset.forEmail("lawyer")).isEmpty();
    assertThat(MailHostPreset.forEmail("lawyer@")).isEmpty();
  }
}
