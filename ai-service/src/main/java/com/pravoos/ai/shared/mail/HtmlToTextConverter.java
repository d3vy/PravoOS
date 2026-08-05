package com.pravoos.ai.shared.mail;

import java.util.Map;
import java.util.regex.Pattern;

public final class HtmlToTextConverter {

  private static final Pattern SCRIPT_OR_STYLE =
      Pattern.compile("(?is)<(script|style)[^>]*>.*?</\\1>");
  private static final Pattern BLOCK_END_TAG =
      Pattern.compile("(?i)<\\s*(/p|/div|/tr|/h[1-6])\\s*>");
  private static final Pattern LINE_BREAK_TAG = Pattern.compile("(?i)<\\s*(br\\s*/?|/li)\\s*>");
  private static final Pattern ANY_TAG = Pattern.compile("(?s)<[^>]+>");
  private static final Pattern COMMENT = Pattern.compile("(?s)<!--.*?-->");
  private static final Pattern REPEATED_BLANK_LINES = Pattern.compile("(?m)(\\s*\\R){3,}");
  private static final Pattern TRAILING_SPACES = Pattern.compile("(?m)[ \\t]+$");
  private static final Pattern LEADING_SPACES = Pattern.compile("(?m)^[ \\t]+");
  private static final Pattern NUMERIC_ENTITY = Pattern.compile("&#(x?)([0-9a-fA-F]+);");

  private static final Map<String, String> NAMED_ENTITIES =
      Map.of(
          "&nbsp;", " ",
          "&amp;", "&",
          "&lt;", "<",
          "&gt;", ">",
          "&quot;", "\"",
          "&apos;", "'",
          "&laquo;", "«",
          "&raquo;", "»",
          "&mdash;", "—",
          "&ndash;", "–");

  private HtmlToTextConverter() {}

  public static String convert(String html) {
    if (html == null || html.isBlank()) {
      return "";
    }
    String text = SCRIPT_OR_STYLE.matcher(html).replaceAll(" ");
    text = COMMENT.matcher(text).replaceAll(" ");
    text = BLOCK_END_TAG.matcher(text).replaceAll("\n\n");
    text = LINE_BREAK_TAG.matcher(text).replaceAll("\n");
    text = ANY_TAG.matcher(text).replaceAll(" ");
    text = decodeEntities(text);
    text = text.replace('\u00A0', ' ').replace("\r\n", "\n").replace('\r', '\n');
    text = TRAILING_SPACES.matcher(text).replaceAll("");
    text = LEADING_SPACES.matcher(text).replaceAll("");
    text = REPEATED_BLANK_LINES.matcher(text).replaceAll("\n\n");
    return text.trim();
  }

  private static String decodeEntities(String text) {
    String decoded = text;
    for (Map.Entry<String, String> entity : NAMED_ENTITIES.entrySet()) {
      decoded = decoded.replace(entity.getKey(), entity.getValue());
    }
    return NUMERIC_ENTITY
        .matcher(decoded)
        .replaceAll(
            match -> {
              int radix = match.group(1).isEmpty() ? 10 : 16;
              try {
                return Character.toString(Integer.parseInt(match.group(2), radix));
              } catch (IllegalArgumentException e) {
                return match.group();
              }
            });
  }
}
