package com.pravoos.llm.pii;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RedactionSession {

  private static final Pattern PLACEHOLDER = Pattern.compile("\\[[A-Z]+_\\d+]");

  private final Map<String, String> placeholderByValue = new LinkedHashMap<>();
  private final Map<String, String> valueByPlaceholder = new LinkedHashMap<>();
  private final Map<String, Integer> counterByLabel = new LinkedHashMap<>();

  String placeholderFor(String label, String value) {
    return placeholderByValue.computeIfAbsent(
        value,
        original -> {
          int index = counterByLabel.merge(label, 1, Integer::sum);
          String placeholder = "[" + label + "_" + index + "]";
          valueByPlaceholder.put(placeholder, original);
          return placeholder;
        });
  }

  public boolean isEmpty() {
    return valueByPlaceholder.isEmpty();
  }

  public int size() {
    return valueByPlaceholder.size();
  }

  public Map<String, String> mapping() {
    return Map.copyOf(valueByPlaceholder);
  }

  public String restore(String text) {
    if (text == null || text.isEmpty() || valueByPlaceholder.isEmpty()) {
      return text;
    }
    Matcher matcher = PLACEHOLDER.matcher(text);
    if (!matcher.find()) {
      return text;
    }
    StringBuilder restored = new StringBuilder(text.length());
    do {
      String original = valueByPlaceholder.get(matcher.group());
      matcher.appendReplacement(
          restored, Matcher.quoteReplacement(original == null ? matcher.group() : original));
    } while (matcher.find());
    matcher.appendTail(restored);
    return restored.toString();
  }
}
