package com.pravoos.ai.shared.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.pravoos.common.security.PiiCryptoHolder;
import com.pravoos.common.security.PiiCryptoProperties;
import com.pravoos.common.security.PiiEncryptor;
import java.security.SecureRandom;
import java.util.Base64;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;

class PiiStringConverterTest {

  private final PiiCryptoHolder holder = new PiiCryptoHolder();
  private final PiiStringConverter converter = new PiiStringConverter();

  private static String randomKey() {
    byte[] key = new byte[32];
    new SecureRandom().nextBytes(key);
    return Base64.getEncoder().encodeToString(key);
  }

  private void wireEncryptor(PiiEncryptor encryptor) {
    ApplicationContext context = mock(ApplicationContext.class);
    when(context.getBean(PiiEncryptor.class)).thenReturn(encryptor);
    holder.setApplicationContext(context);
  }

  @AfterEach
  void resetStaticHolder() {
    holder.setApplicationContext(null);
  }

  @Test
  void encryptsOnWriteAndDecryptsOnReadRoundTrip() {
    wireEncryptor(
        new PiiEncryptor(
            new PiiCryptoProperties(false, "v1", java.util.Map.of("v1", randomKey()), null)));

    String stored = converter.convertToDatabaseColumn("Иванов Иван Иванович");

    assertThat(stored).startsWith("pii2:v1:");
    assertThat(converter.convertToEntityAttribute(stored)).isEqualTo("Иванов Иван Иванович");
  }

  @Test
  void nullAttributePassesThroughOnWrite() {
    wireEncryptor(
        new PiiEncryptor(
            new PiiCryptoProperties(false, "v1", java.util.Map.of("v1", randomKey()), null)));

    assertThat(converter.convertToDatabaseColumn(null)).isNull();
  }

  @Test
  void nullColumnPassesThroughOnRead() {
    wireEncryptor(
        new PiiEncryptor(
            new PiiCryptoProperties(false, "v1", java.util.Map.of("v1", randomKey()), null)));

    assertThat(converter.convertToEntityAttribute(null)).isNull();
  }

  @Test
  void writesPlaintextWhenEncryptionDisabled() {
    wireEncryptor(new PiiEncryptor(new PiiCryptoProperties(false, null, null, null)));

    assertThat(converter.convertToDatabaseColumn("plain")).isEqualTo("plain");
    assertThat(converter.convertToEntityAttribute("plain")).isEqualTo("plain");
  }

  @Test
  void delegatesToCurrentHolderEncryptorAtCallTime() {
    holder.setApplicationContext(null);

    assertThatThrownByAccessingConverterWithoutContext();
  }

  private void assertThatThrownByAccessingConverterWithoutContext() {
    org.junit.jupiter.api.Assertions.assertThrows(
        IllegalStateException.class, () -> converter.convertToDatabaseColumn("x"));
  }
}
