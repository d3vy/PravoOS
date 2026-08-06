package com.pravoos.ai.practice.internal.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.pravoos.ai.practice.internal.dto.SignatureProtocolModel;
import com.pravoos.ai.shared.model.enums.SignatureProviderType;
import com.pravoos.ai.shared.model.enums.SignatureSignerRole;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.UUID;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

class SignatureProtocolPdfWriterTest {

  private final SignatureProtocolPdfWriter writer = new SignatureProtocolPdfWriter();

  private String extractText(byte[] pdfBytes) throws IOException {
    try (PDDocument document = Loader.loadPDF(pdfBytes)) {
      return new PDFTextStripper().getText(document).replace("\n", " ");
    }
  }

  private SignatureProtocolModel simpleModel() {
    return new SignatureProtocolModel(
        UUID.randomUUID(),
        "Дело о взыскании",
        "Договор оказания услуг",
        "abc123hash",
        SignatureProviderType.SIMPLE,
        SignatureSignerRole.CLIENT,
        "Иванов Иван",
        "203.0.113.5",
        "Mozilla/5.0",
        "Я согласен подписать документ",
        LocalDateTime.of(2026, 8, 1, 10, 0),
        LocalDateTime.of(2026, 8, 1, 10, 5),
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        false);
  }

  private SignatureProtocolModel qualifiedModel(boolean chainVerified) {
    return new SignatureProtocolModel(
        UUID.randomUUID(),
        "Дело о взыскании",
        "Договор оказания услуг",
        "abc123hash",
        SignatureProviderType.DETACHED_CMS,
        SignatureSignerRole.LAWYER,
        "Петров Пётр",
        "203.0.113.6",
        "curl/8.0",
        "Подписано КЭП",
        LocalDateTime.of(2026, 8, 1, 10, 0),
        LocalDateTime.of(2026, 8, 1, 10, 5),
        LocalDateTime.of(2026, 8, 1, 10, 4),
        "CN=Петров Пётр",
        "CN=УЦ Тест",
        "1234567890",
        LocalDateTime.of(2026, 1, 1, 0, 0),
        LocalDateTime.of(2027, 1, 1, 0, 0),
        "GOST3411-2012-256",
        chainVerified);
  }

  @Test
  void generatesSimpleSignatureProtocolWithIpAndUserAgent() throws IOException {
    SignatureProtocolModel model = simpleModel();

    byte[] pdf = writer.write(model);

    assertThat(pdf).isNotEmpty();
    String text = extractText(pdf);
    assertThat(text).contains("Протокол подписания документа");
    assertThat(text).contains("Простая электронная подпись");
    assertThat(text).contains("Клиент");
    assertThat(text).contains("IP-адрес подписанта: 203.0.113.5");
    assertThat(text).contains("Браузер подписанта: Mozilla/5.0");
    assertThat(text).doesNotContain("Сертификат подписи");
  }

  @Test
  void generatesQualifiedSignatureProtocolWithCertificateAndNoIp() throws IOException {
    SignatureProtocolModel model = qualifiedModel(true);

    String text = extractText(writer.write(model));

    assertThat(text).contains("Усиленная электронная подпись");
    assertThat(text).contains("Юрист (исполнитель)");
    assertThat(text).contains("Сертификат подписи");
    assertThat(text).contains("CN=Петров Пётр");
    assertThat(text).contains("GOST3411-2012-256");
    assertThat(text).doesNotContain("IP-адрес подписанта");
    assertThat(text).doesNotContain("Браузер подписанта");
    assertThat(text).contains("Цепочка сертификата проверена");
  }

  @Test
  void footnoteReflectsUnverifiedChain() throws IOException {
    SignatureProtocolModel model = qualifiedModel(false);

    String text = extractText(writer.write(model));

    assertThat(text).contains("в область проверки не входит");
  }

  @Test
  void omitsDeclaredSigningTimeWhenAbsent() throws IOException {
    SignatureProtocolModel model = simpleModel();

    String text = extractText(writer.write(model));

    assertThat(text).doesNotContain("Время подписания в контейнере");
  }
}
