package com.pravoos.ai.shared.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TextPreviewTest {

  @Test
  void returnsTheTextUnchangedWhenItFitsTheLimit() {
    assertThat(TextPreview.clamp("короткий текст", 140)).isEqualTo("короткий текст");
  }

  @Test
  void returnsTheTextUnchangedWhenItExactlyFillsTheLimit() {
    String text = "x".repeat(140);

    assertThat(TextPreview.clamp(text, 140)).isEqualTo(text);
  }

  @Test
  void appendsAnEllipsisWhenTheTextIsOneCharacterTooLong() {
    String text = "x".repeat(141);

    assertThat(TextPreview.clamp(text, 140)).isEqualTo("x".repeat(140) + "…");
  }

  @Test
  void passesNullThrough() {
    assertThat(TextPreview.clamp(null, 140)).isNull();
  }

  @Test
  void doesNotSplitASurrogatePairAtTheCutPoint() {
    String text = "аб😀вг";

    String clamped = TextPreview.clamp(text, 3);

    assertThat(clamped).isEqualTo("аб…");
    assertThat(clamped.chars().anyMatch(codePoint -> Character.isSurrogate((char) codePoint)))
        .isFalse();
  }

  @Test
  void keepsAWholeSurrogatePairWhenItFitsEntirely() {
    assertThat(TextPreview.clamp("аб😀вг", 4)).isEqualTo("аб😀…");
  }

  @Test
  void collapsesToAnEllipsisWhenTheLimitIsNotPositive() {
    assertThat(TextPreview.clamp("текст", 0)).isEqualTo("…");
  }

  @Test
  void returnsEmptyTextUnchangedRegardlessOfLimit() {
    assertThat(TextPreview.clamp("", 0)).isEmpty();
  }
}
