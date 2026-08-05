package com.pravoos.llm.pii;

import java.util.LinkedHashMap;
import java.util.Map;

public class RedactionSession {

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
    String restored = text;
    for (Map.Entry<String, String> entry : valueByPlaceholder.entrySet()) {
      restored = restored.replace(entry.getKey(), entry.getValue());
    }
    return restored;
  }
}
