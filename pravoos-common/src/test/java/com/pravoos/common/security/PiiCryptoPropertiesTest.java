package com.pravoos.common.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class PiiCryptoPropertiesTest {

  @Test
  void nullKeysMapDefaultsToEmpty() {
    PiiCryptoProperties properties = new PiiCryptoProperties(false, null, null, null);

    assertTrue(properties.keys().isEmpty());
    assertTrue(properties.resolvedKeys().isEmpty());
  }

  @Test
  void resolvedKeysMergesLegacySingleKeyUnderLegacyId() {
    PiiCryptoProperties properties =
        new PiiCryptoProperties(false, null, Map.of("v1", "keyA"), "legacyKeyValue");

    Map<String, String> resolved = properties.resolvedKeys();

    assertEquals("keyA", resolved.get("v1"));
    assertEquals("legacyKeyValue", resolved.get(PiiCryptoProperties.LEGACY_KEY_ID));
  }

  @Test
  void resolvedKeysDoesNotOverrideExplicitLegacyEntry() {
    PiiCryptoProperties properties =
        new PiiCryptoProperties(
            false, null, Map.of(PiiCryptoProperties.LEGACY_KEY_ID, "explicit"), "fromKeyField");

    assertEquals("explicit", properties.resolvedKeys().get(PiiCryptoProperties.LEGACY_KEY_ID));
  }

  @Test
  void resolvedActiveKeyIdPrefersExplicitValue() {
    PiiCryptoProperties properties =
        new PiiCryptoProperties(false, " v2 ", Map.of("v1", "a", "v2", "b"), null);

    assertEquals("v2", properties.resolvedActiveKeyId());
  }

  @Test
  void resolvedActiveKeyIdInfersSingleKeyWhenNotSet() {
    PiiCryptoProperties properties = new PiiCryptoProperties(false, null, null, "onlyKey");

    assertEquals(PiiCryptoProperties.LEGACY_KEY_ID, properties.resolvedActiveKeyId());
  }

  @Test
  void resolvedActiveKeyIdIsNullWhenAmbiguousAndNotSet() {
    PiiCryptoProperties properties =
        new PiiCryptoProperties(false, null, Map.of("v1", "a", "v2", "b"), null);

    assertNull(properties.resolvedActiveKeyId());
  }

  @Test
  void resolvedActiveKeyIdIsNullWhenNoKeysAtAll() {
    PiiCryptoProperties properties = new PiiCryptoProperties(false, null, null, null);

    assertNull(properties.resolvedActiveKeyId());
  }
}
