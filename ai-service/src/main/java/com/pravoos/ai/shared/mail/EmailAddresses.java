package com.pravoos.ai.shared.mail;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class EmailAddresses {

  private static final Pattern ADDRESS =
      Pattern.compile("[\\w.!#$%&'*+/=?^`{|}~-]+@[\\w-]+(?:\\.[\\w-]+)+");

  private EmailAddresses() {}

  public static Set<String> extractAll(String... headerValues) {
    Set<String> addresses = new LinkedHashSet<>();
    if (headerValues == null) {
      return addresses;
    }
    for (String headerValue : headerValues) {
      if (headerValue == null || headerValue.isBlank()) {
        continue;
      }
      Matcher matcher = ADDRESS.matcher(headerValue);
      while (matcher.find()) {
        addresses.add(normalize(matcher.group()));
      }
    }
    return addresses;
  }

  public static Optional<String> extractFirst(String headerValue) {
    return extractAll(headerValue).stream().findFirst();
  }

  public static String normalize(String address) {
    return address == null ? null : address.trim().toLowerCase(Locale.ROOT);
  }
}
