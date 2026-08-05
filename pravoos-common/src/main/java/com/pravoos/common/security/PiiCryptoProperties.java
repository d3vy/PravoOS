package com.pravoos.common.security;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "pii.crypto")
public record PiiCryptoProperties(
    boolean required, String activeKeyId, Map<String, String> keys, String key) {

  public static final String LEGACY_KEY_ID = "legacy";

  public PiiCryptoProperties {
    keys = keys == null ? Map.of() : Map.copyOf(keys);
  }

  public Map<String, String> resolvedKeys() {
    if (key == null || key.isBlank()) {
      return keys;
    }
    Map<String, String> resolved = new LinkedHashMap<>(keys);
    resolved.putIfAbsent(LEGACY_KEY_ID, key.trim());
    return resolved;
  }

  public String resolvedActiveKeyId() {
    if (activeKeyId != null && !activeKeyId.isBlank()) {
      return activeKeyId.trim();
    }
    Map<String, String> resolved = resolvedKeys();
    return resolved.size() == 1 ? resolved.keySet().iterator().next() : null;
  }
}
