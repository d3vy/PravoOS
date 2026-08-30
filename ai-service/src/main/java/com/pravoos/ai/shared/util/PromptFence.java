package com.pravoos.ai.shared.util;

import java.util.regex.Pattern;

public final class PromptFence {

  private static final Pattern CONTROL_CHARS = Pattern.compile("[\\p{Cntrl}&&[^\\r\\n\\t]]");
  private static final Pattern FENCE_MARKERS = Pattern.compile("<<<[^<>\\r\\n]{0,64}>>>");

  private final String open;
  private final String close;
  private final String blankPlaceholder;

  public PromptFence(String label) {
    this(label, "");
  }

  public PromptFence(String label, String blankPlaceholder) {
    this.open = "<<<" + label + "_НАЧАЛО>>>";
    this.close = "<<<" + label + "_КОНЕЦ>>>";
    this.blankPlaceholder = blankPlaceholder;
  }

  public String sanitize(String text) {
    if (text == null || text.isBlank()) {
      return blankPlaceholder;
    }
    return FENCE_MARKERS
        .matcher(CONTROL_CHARS.matcher(text).replaceAll(" "))
        .replaceAll(" ")
        .strip();
  }

  public String wrap(String text) {
    return open + "\n" + sanitize(text) + "\n" + close;
  }
}
