package com.pravoos.ai.core.internal.service;

import java.util.regex.Pattern;

final class PromptFence {

  private static final Pattern CONTROL_CHARS = Pattern.compile("[\\p{Cntrl}&&[^\\r\\n\\t]]");

  private final String open;
  private final String close;
  private final Pattern markers;
  private final String blankPlaceholder;

  PromptFence(String label) {
    this(label, "");
  }

  PromptFence(String label, String blankPlaceholder) {
    this.open = "<<<" + label + "_НАЧАЛО>>>";
    this.close = "<<<" + label + "_КОНЕЦ>>>";
    this.markers = Pattern.compile(Pattern.quote(open) + "|" + Pattern.quote(close));
    this.blankPlaceholder = blankPlaceholder;
  }

  String sanitize(String text) {
    if (text == null || text.isBlank()) {
      return blankPlaceholder;
    }
    return markers.matcher(CONTROL_CHARS.matcher(text).replaceAll(" ")).replaceAll(" ").strip();
  }

  String wrap(String text) {
    return open + "\n" + sanitize(text) + "\n" + close;
  }
}
