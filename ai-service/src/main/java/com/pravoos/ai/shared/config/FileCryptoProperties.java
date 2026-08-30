package com.pravoos.ai.shared.config;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "document.crypto")
public record FileCryptoProperties(String activeKeyId, Map<String, String> keys, String key) {

  public static final String LEGACY_KEY_ID = "legacy";

  public FileCryptoProperties {
    keys = keys == null ? Map.of() : Map.copyOf(keys);
  }

  public static FileCryptoProperties ofSingleKey(String key) {
    return new FileCryptoProperties(null, Map.of(), key);
  }

  public boolean hasKey() {
    return !resolvedKeys().isEmpty();
  }

  public Map<String, String> resolvedKeys() {
    Map<String, String> resolved = new LinkedHashMap<>();
    keys.forEach(
        (keyId, keyMaterial) -> {
          if (keyMaterial != null && !keyMaterial.isBlank()) {
            resolved.put(keyId, keyMaterial.trim());
          }
        });
    if (key != null && !key.isBlank()) {
      resolved.putIfAbsent(LEGACY_KEY_ID, key.trim());
    }
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
