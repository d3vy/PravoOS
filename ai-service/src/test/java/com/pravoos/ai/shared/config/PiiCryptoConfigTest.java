package com.pravoos.ai.shared.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.pravoos.common.security.PiiCryptoHolder;
import com.pravoos.common.security.PiiCryptoProperties;
import com.pravoos.common.security.PiiEncryptor;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PiiCryptoConfigTest {

  private final PiiCryptoConfig config = new PiiCryptoConfig();

  private static String randomKey() {
    byte[] key = new byte[32];
    new SecureRandom().nextBytes(key);
    return Base64.getEncoder().encodeToString(key);
  }

  @Test
  void piiEncryptorBeanIsBuiltFromGivenProperties() {
    PiiCryptoProperties properties = new PiiCryptoProperties(false, null, Map.of(), randomKey());

    PiiEncryptor encryptor = config.piiEncryptor(properties);

    assertThat(encryptor).isNotNull();
    assertThat(encryptor.isEncryptionEnabled()).isTrue();
  }

  @Test
  void piiCryptoHolderBeanIsFreshInstance() {
    PiiCryptoHolder first = config.piiCryptoHolder();
    PiiCryptoHolder second = config.piiCryptoHolder();

    assertThat(first).isNotNull();
    assertThat(second).isNotNull();
    assertThat(first).isNotSameAs(second);
  }
}
