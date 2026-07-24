package com.pravoos.user.shared.util;

public final class EmailMasker {

  private EmailMasker() {}

  public static String mask(String email) {
    if (email == null || email.isBlank()) {
      return "***";
    }
    int at = email.indexOf('@');
    if (at <= 0) {
      return "***";
    }
    String localPart = email.substring(0, at);
    String domain = email.substring(at);
    String visible = localPart.substring(0, 1);
    return visible + "***" + domain;
  }
}
