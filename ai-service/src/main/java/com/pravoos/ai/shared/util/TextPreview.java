package com.pravoos.ai.shared.util;

public final class TextPreview {

  private static final String ELLIPSIS = "…";

  private TextPreview() {}

  public static String clamp(String text, int maxLength) {
    if (text == null || text.length() <= maxLength) {
      return text;
    }
    if (maxLength <= 0) {
      return ELLIPSIS;
    }
    int end = Character.isHighSurrogate(text.charAt(maxLength - 1)) ? maxLength - 1 : maxLength;
    return text.substring(0, end) + ELLIPSIS;
  }
}
