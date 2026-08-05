package com.pravoos.ai.shared.mail;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class HtmlToTextConverterTest {

  @Test
  void stripsTagsAndKeepsReadableText() {
    String text =
        HtmlToTextConverter.convert("<div><p>Здравствуйте,</p><p>договор во вложении.</p></div>");

    assertThat(text).contains("Здравствуйте,");
    assertThat(text).contains("договор во вложении.");
    assertThat(text).doesNotContain("<");
  }

  @Test
  void dropsScriptAndStyleContent() {
    String text =
        HtmlToTextConverter.convert(
            "<style>.a{color:red}</style><script>alert('x')</script><p>Текст</p>");

    assertThat(text).isEqualTo("Текст");
  }

  @Test
  void decodesEntities() {
    String text = HtmlToTextConverter.convert("<p>&laquo;Ромашка&raquo; &amp; &#1050;&#1086;</p>");

    assertThat(text).isEqualTo("«Ромашка» & Ко");
  }

  @Test
  void collapsesRepeatedBlankLines() {
    String text = HtmlToTextConverter.convert("<p>Первый</p><br><br><br><p>Второй</p>");

    assertThat(text).isEqualTo("Первый\n\nВторой");
  }

  @Test
  void returnsEmptyStringForBlankInput() {
    assertThat(HtmlToTextConverter.convert(null)).isEmpty();
    assertThat(HtmlToTextConverter.convert("   ")).isEmpty();
  }
}
