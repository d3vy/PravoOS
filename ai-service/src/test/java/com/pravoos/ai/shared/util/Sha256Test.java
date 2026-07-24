package com.pravoos.ai.shared.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class Sha256Test {

  @Test
  void emptyInputMatchesKnownDigest() {
    assertThat(Sha256.hex(new byte[0]))
        .isEqualTo("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
  }

  @Test
  void knownStringMatchesKnownDigest() {
    assertThat(Sha256.hex("abc"))
        .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
  }

  @Test
  void digestIsDeterministic() {
    byte[] content =
        "Договор оказания юридических услуг".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    assertThat(Sha256.hex(content)).isEqualTo(Sha256.hex(content));
  }

  @Test
  void differentContentProducesDifferentDigest() {
    assertThat(Sha256.hex("v1")).isNotEqualTo(Sha256.hex("v2"));
  }

  @Test
  void digestIsSixtyFourHexChars() {
    assertThat(Sha256.hex("anything")).hasSize(64).matches("[0-9a-f]{64}");
  }
}
