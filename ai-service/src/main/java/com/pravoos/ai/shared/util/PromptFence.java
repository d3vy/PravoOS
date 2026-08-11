package com.pravoos.ai.shared.util;

import java.util.regex.Pattern;

public final class PromptFence {

  private static final Pattern CONTROL_CHARS = Pattern.compile("[\\p{Cntrl}&&[^\\r\\n\\t]]");

  private final String open;
  private final String close;
  private final Pattern markers;
  private final String blankPlaceholder;

  public PromptFence(String label) {
    this(label, "");
  }

  public PromptFence(String label, String blankPlaceholder) {
    this.open = "<<<" + label + "_НАЧАЛО>>>";
    this.close = "<<<" + label + "_КОНЕЦ>>>";
    this.markers = Pattern.compile(Pattern.quote(open) + "|" + Pattern.quote(close));
    this.blankPlaceholder = blankPlaceholder;
  }

  public String sanitize(String text) {
    if (text == null || text.isBlank()) {
      return blankPlaceholder;
    }
    return markers.matcher(CONTROL_CHARS.matcher(text).replaceAll(" ")).replaceAll(" ").strip();
  }

  public String wrap(String text) {
    return open + "\n" + sanitize(text) + "\n" + close;
  }
}
