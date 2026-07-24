package com.pravoos.user.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.pravoos.user.identity.internal.security.Base32;
import com.pravoos.user.identity.internal.security.TotpGenerator;
import org.junit.jupiter.api.Test;

class TotpGeneratorTest {

  private final TotpGenerator totpGenerator = new TotpGenerator();

  @Test
  void base32RoundTripPreservesBytes() {
    byte[] original = "12345678901234567890".getBytes();
    String encoded = Base32.encode(original);
    assertThat(Base32.decode(encoded)).isEqualTo(original);
  }

  @Test
  void verifyAcceptsFreshlyGeneratedCode() {
    String secret = totpGenerator.generateSecret();
    String code = bruteForceCurrent(secret);
    assertThat(totpGenerator.verify(secret, code)).isTrue();
  }

  @Test
  void verifyRejectsWrongCode() {
    String secret = totpGenerator.generateSecret();
    assertThat(totpGenerator.verify(secret, "000000")).isFalse();
    assertThat(totpGenerator.verify(secret, "12345")).isFalse();
    assertThat(totpGenerator.verify(secret, null)).isFalse();
  }

  @Test
  void otpAuthUriContainsIssuerAndSecret() {
    String secret = totpGenerator.generateSecret();
    String uri = totpGenerator.otpAuthUri(secret, "lawyer@example.com", "PravoOS");
    assertThat(uri).startsWith("otpauth://totp/PravoOS:");
    assertThat(uri).contains("secret=" + secret);
    assertThat(uri).contains("issuer=PravoOS");
  }

  private String bruteForceCurrent(String secret) {
    for (int i = 0; i < 1_000_000; i++) {
      String code = String.format("%06d", i);
      if (totpGenerator.verify(secret, code)) {
        return code;
      }
    }
    throw new IllegalStateException("no matching code");
  }
}
