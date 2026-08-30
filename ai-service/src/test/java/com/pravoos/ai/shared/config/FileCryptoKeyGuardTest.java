package com.pravoos.ai.shared.config;

import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatNoException;

import org.junit.jupiter.api.Test;

class FileCryptoKeyGuardTest {

  @Test
  void verifyKeyPresentThrowsWhenKeyMissing() {
    FileCryptoKeyGuard guard = new FileCryptoKeyGuard(FileCryptoProperties.ofSingleKey(null));

    assertThatIllegalStateException()
        .isThrownBy(guard::verifyKeyPresent)
        .withMessageContaining("FILE_ENCRYPTION_KEY");
  }

  @Test
  void verifyKeyPresentPassesWhenKeyConfigured() {
    FileCryptoKeyGuard guard =
        new FileCryptoKeyGuard(FileCryptoProperties.ofSingleKey("secret-key"));

    assertThatNoException().isThrownBy(guard::verifyKeyPresent);
  }
}
