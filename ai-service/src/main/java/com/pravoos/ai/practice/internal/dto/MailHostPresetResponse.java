package com.pravoos.ai.practice.internal.dto;

import com.pravoos.ai.shared.mail.MailHostPreset;
import java.util.List;

public record MailHostPresetResponse(
    String id,
    String displayName,
    String imapHost,
    int imapPort,
    boolean imapSsl,
    List<String> domains) {

  public static MailHostPresetResponse from(MailHostPreset preset) {
    return new MailHostPresetResponse(
        preset.name(),
        preset.getDisplayName(),
        preset.getImapHost(),
        preset.getImapPort(),
        preset.isSsl(),
        preset.getDomains());
  }
}
