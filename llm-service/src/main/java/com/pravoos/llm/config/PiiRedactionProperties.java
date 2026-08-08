package com.pravoos.llm.config;

import com.pravoos.llm.pii.PiiPattern;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "llm.pii.redaction")
public record PiiRedactionProperties(Boolean enabled, Boolean embeddings, String categories) {

  public boolean isEnabled() {
    return enabled == null || enabled;
  }

  public boolean isEmbeddingsEnabled() {
    return isEnabled() && (embeddings == null || embeddings);
  }

  public boolean isCategoryEnabled(PiiPattern pattern) {
    return enabledCategories().contains(pattern);
  }

  public Set<PiiPattern> enabledCategories() {
    if (categories == null || categories.isBlank() || "all".equalsIgnoreCase(categories.trim())) {
      return EnumSet.allOf(PiiPattern.class);
    }
    List<String> requested =
        Arrays.stream(categories.split(",")).map(String::trim).map(String::toUpperCase).toList();
    Set<PiiPattern> enabledSet = EnumSet.noneOf(PiiPattern.class);
    for (PiiPattern pattern : PiiPattern.values()) {
      if (requested.contains(pattern.name()) || requested.contains(pattern.label())) {
        enabledSet.add(pattern);
      }
    }
    return enabledSet;
  }
}
