package com.pravoos.ai.shared.mail;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public enum MailHostPreset {
  YANDEX("Яндекс.Почта", "imap.yandex.ru", 993, true, List.of("yandex.ru", "ya.ru", "yandex.com")),
  MAIL_RU(
      "Mail.ru",
      "imap.mail.ru",
      993,
      true,
      List.of("mail.ru", "inbox.ru", "bk.ru", "list.ru", "internet.ru")),
  GMAIL("Gmail", "imap.gmail.com", 993, true, List.of("gmail.com", "googlemail.com"));

  private final String displayName;
  private final String imapHost;
  private final int imapPort;
  private final boolean ssl;
  private final List<String> domains;

  MailHostPreset(
      String displayName, String imapHost, int imapPort, boolean ssl, List<String> domains) {
    this.displayName = displayName;
    this.imapHost = imapHost;
    this.imapPort = imapPort;
    this.ssl = ssl;
    this.domains = domains;
  }

  public String getDisplayName() {
    return displayName;
  }

  public String getImapHost() {
    return imapHost;
  }

  public int getImapPort() {
    return imapPort;
  }

  public boolean isSsl() {
    return ssl;
  }

  public List<String> getDomains() {
    return domains;
  }

  public static Optional<MailHostPreset> forEmail(String emailAddress) {
    if (emailAddress == null || emailAddress.isBlank()) {
      return Optional.empty();
    }
    int at = emailAddress.lastIndexOf('@');
    if (at < 0 || at == emailAddress.length() - 1) {
      return Optional.empty();
    }
    String domain = emailAddress.substring(at + 1).trim().toLowerCase(Locale.ROOT);
    return Arrays.stream(values()).filter(preset -> preset.domains.contains(domain)).findFirst();
  }
}
