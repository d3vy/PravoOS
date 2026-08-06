package com.pravoos.ai.shared.signature;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pravoos.ai.shared.config.SignatureProperties;
import com.pravoos.ai.shared.exception.TrustedCaStoreException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TrustedCaStoreTest {

  @TempDir Path directory;

  private static TrustedCaStore store(String trustedCaPath) {
    return new TrustedCaStore(
        new SignatureProperties(
            30,
            new SignatureProperties.Diadoc(null, null),
            new SignatureProperties.Cms(trustedCaPath)));
  }

  @Test
  void isNotConfigured_whenPathIsBlank() {
    assertThat(store("  ").configured()).isFalse();
    assertThat(store(null).anchors()).isEmpty();
  }

  @Test
  void loadsAnchorsFromDirectory() throws IOException {
    Files.writeString(
        directory.resolve("ca.pem"),
        CmsTestSignatures.signatureIssuedByCa("текст".getBytes(StandardCharsets.UTF_8), "Подписант")
            .caCertificatePem());

    TrustedCaStore loaded = store(directory.toString());

    assertThat(loaded.configured()).isTrue();
    assertThat(loaded.anchors()).hasSize(1);
  }

  @Test
  void failsFast_whenPathIsMissing() {
    String missing = directory.resolve("нет-такого-каталога").toString();

    assertThatThrownBy(() -> store(missing))
        .isInstanceOf(TrustedCaStoreException.class)
        .hasMessageContaining("не найден");
  }

  @Test
  void failsFast_whenDirectoryHasNoCertificates() throws IOException {
    Files.writeString(directory.resolve("readme.txt"), "не сертификат");

    String path = directory.toString();

    assertThatThrownBy(() -> store(path))
        .isInstanceOf(TrustedCaStoreException.class)
        .hasMessageContaining("не найдено ни одного сертификата");
  }
}
