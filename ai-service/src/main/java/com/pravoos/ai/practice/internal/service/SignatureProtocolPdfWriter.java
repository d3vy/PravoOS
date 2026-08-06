package com.pravoos.ai.practice.internal.service;

import com.pravoos.ai.practice.internal.dto.SignatureProtocolModel;
import com.pravoos.ai.shared.exception.CaseExportException;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.springframework.stereotype.Component;

@Component
public class SignatureProtocolPdfWriter {

  private static final String REGULAR_FONT = "/fonts/DejaVuSans.ttf";
  private static final String BOLD_FONT = "/fonts/DejaVuSans-Bold.ttf";
  private static final DateTimeFormatter TIMESTAMP =
      DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");

  private static final float MARGIN = 50f;
  private static final float PAGE_WIDTH = PDRectangle.A4.getWidth();
  private static final float PAGE_HEIGHT = PDRectangle.A4.getHeight();
  private static final float CONTENT_WIDTH = PAGE_WIDTH - 2 * MARGIN;

  private static final Color MUTED = new Color(0.45f, 0.47f, 0.5f);

  public byte[] write(SignatureProtocolModel model) {
    try (PDDocument document = new PDDocument();
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {

      PDType0Font regular = loadFont(document, REGULAR_FONT);
      PDType0Font bold = loadFont(document, BOLD_FONT);
      Renderer renderer = new Renderer(document, regular, bold);

      renderer.title("Протокол подписания документа");
      renderer.muted("Идентификатор подписи: " + model.signatureId());
      renderer.gap(10f);

      renderer.heading("Документ");
      renderer.labeled("Наименование", model.documentTitle());
      renderer.labeled("Дело", model.caseTitle());
      renderer.labeled("Хеш документа (SHA-256)", model.documentHash());
      renderer.gap(8f);

      renderer.heading("Подпись");
      renderer.labeled("Вид подписи", providerLabel(model));
      renderer.labeled("Подписант", model.signerName());
      renderer.labeled("Сторона подписания", signerRoleLabel(model));
      renderer.labeled("Запрос создан", timestamp(model.requestedAt()));
      renderer.labeled("Подписано", timestamp(model.signedAt()));
      if (model.declaredSigningTime() != null) {
        renderer.labeled("Время подписания в контейнере", timestamp(model.declaredSigningTime()));
      }
      if (!model.isQualified()) {
        renderer.labeled("IP-адрес подписанта", model.signerIp());
        renderer.labeled("Браузер подписанта", model.signerUserAgent());
      }
      renderer.gap(8f);

      if (model.isQualified()) {
        renderer.heading("Сертификат подписи");
        renderer.labeled("Владелец", model.certificateSubject());
        renderer.labeled("Издатель", model.certificateIssuer());
        renderer.labeled("Серийный номер", model.certificateSerial());
        renderer.labeled("Действителен с", timestamp(model.certificateValidFrom()));
        renderer.labeled("Действителен по", timestamp(model.certificateValidTo()));
        renderer.labeled("Алгоритм", model.signatureAlgorithm());
        renderer.gap(8f);
      }

      renderer.heading("Подтверждение");
      renderer.body(model.consentText());
      renderer.gap(10f);
      renderer.muted(footnote(model));

      renderer.finish();
      document.save(outputStream);
      return outputStream.toByteArray();
    } catch (IOException ex) {
      throw new CaseExportException("Failed to generate signature protocol .pdf", ex);
    }
  }

  private String signerRoleLabel(SignatureProtocolModel model) {
    return switch (model.signerRole()) {
      case CLIENT -> "Клиент";
      case LAWYER -> "Юрист (исполнитель)";
    };
  }

  private String providerLabel(SignatureProtocolModel model) {
    return switch (model.provider()) {
      case SIMPLE -> "Простая электронная подпись (ст. 5 63-ФЗ)";
      case DETACHED_CMS ->
          "Усиленная электронная подпись, открепленный контейнер CMS/PKCS#7 (ст. 6 63-ФЗ)";
      case DIADOC -> "Квалифицированная электронная подпись через Контур.Диадок";
    };
  }

  private String footnote(SignatureProtocolModel model) {
    if (model.isQualified()) {
      String chainNote =
          model.chainVerified()
              ? "Цепочка сертификата проверена до корневого сертификата из доверенного списка "
                  + "аккредитованных удостоверяющих центров, настроенного в системе."
              : "Проверка сертификата по цепочке аккредитованного удостоверяющего центра "
                  + "в область проверки не входит.";
      return "Протокол сформирован PravoOS автоматически. Криптографическая проверка подписи выполнена "
          + "против содержимого документа с указанным хешем. "
          + chainNote;
    }
    return "Протокол сформирован PravoOS автоматически. Подписант идентифицирован по учётной записи "
        + "клиентского портала; зафиксированы IP-адрес, браузер и время подтверждения.";
  }

  private String timestamp(LocalDateTime moment) {
    return moment == null ? null : TIMESTAMP.format(moment) + " UTC";
  }

  private PDType0Font loadFont(PDDocument document, String resourcePath) throws IOException {
    try (InputStream fontStream = getClass().getResourceAsStream(resourcePath)) {
      if (fontStream == null) {
        throw new IOException("Embedded font not found on classpath: " + resourcePath);
      }
      return PDType0Font.load(document, fontStream);
    }
  }

  private static final class Renderer {

    private final PDDocument document;
    private final PDType0Font regular;
    private final PDType0Font bold;
    private PDPageContentStream content;
    private float cursorY;

    private Renderer(PDDocument document, PDType0Font regular, PDType0Font bold)
        throws IOException {
      this.document = document;
      this.regular = regular;
      this.bold = bold;
      newPage();
    }

    void title(String text) throws IOException {
      ensureSpace(26f);
      drawText(sanitize(bold, text), bold, 18f, MARGIN, cursorY, Color.BLACK);
      cursorY -= 26f;
    }

    void heading(String text) throws IOException {
      ensureSpace(20f);
      drawText(sanitize(bold, text), bold, 13f, MARGIN, cursorY, Color.BLACK);
      cursorY -= 18f;
    }

    void muted(String text) throws IOException {
      paragraph(text, regular, 9.5f, MUTED);
    }

    void body(String text) throws IOException {
      paragraph(text, regular, 11f, Color.BLACK);
    }

    void labeled(String label, String value) throws IOException {
      paragraph(
          label + ": " + (value == null || value.isBlank() ? "—" : value),
          regular,
          11f,
          Color.BLACK);
    }

    void gap(float height) throws IOException {
      if (cursorY - height < MARGIN) {
        newPage();
      } else {
        cursorY -= height;
      }
    }

    void finish() throws IOException {
      if (content != null) {
        content.close();
        content = null;
      }
    }

    private void paragraph(String text, PDType0Font font, float size, Color color)
        throws IOException {
      float leading = size * 1.35f;
      for (String logicalLine : text.replace("\t", "    ").split("\\r?\\n", -1)) {
        String safe = sanitize(font, logicalLine);
        if (safe.isEmpty()) {
          gap(leading);
          continue;
        }
        for (String visualLine : wrap(font, size, safe, CONTENT_WIDTH)) {
          ensureSpace(leading);
          drawText(visualLine, font, size, MARGIN, cursorY, color);
          cursorY -= leading;
        }
      }
    }

    private void drawText(String text, PDType0Font font, float size, float x, float y, Color color)
        throws IOException {
      content.beginText();
      content.setFont(font, size);
      content.setNonStrokingColor(color);
      content.newLineAtOffset(x, y);
      content.showText(text);
      content.endText();
    }

    private void ensureSpace(float needed) throws IOException {
      if (cursorY - needed < MARGIN) {
        newPage();
      }
    }

    private void newPage() throws IOException {
      if (content != null) {
        content.close();
      }
      PDPage page = new PDPage(PDRectangle.A4);
      document.addPage(page);
      content = new PDPageContentStream(document, page);
      cursorY = PAGE_HEIGHT - MARGIN;
    }

    private List<String> wrap(PDType0Font font, float size, String text, float maxWidth)
        throws IOException {
      List<String> lines = new ArrayList<>();
      StringBuilder current = new StringBuilder();
      for (String word : splitLongWords(font, size, text, maxWidth)) {
        if (current.length() == 0) {
          current.append(word);
        } else if (textWidth(font, size, current + " " + word) <= maxWidth) {
          current.append(' ').append(word);
        } else {
          lines.add(current.toString());
          current.setLength(0);
          current.append(word);
        }
      }
      lines.add(current.toString());
      return lines;
    }

    private List<String> splitLongWords(PDType0Font font, float size, String text, float maxWidth)
        throws IOException {
      List<String> parts = new ArrayList<>();
      for (String word : text.split(" ", -1)) {
        if (textWidth(font, size, word) <= maxWidth) {
          parts.add(word);
          continue;
        }
        StringBuilder chunk = new StringBuilder();
        for (int index = 0; index < word.length(); index++) {
          chunk.append(word.charAt(index));
          if (textWidth(font, size, chunk.toString()) > maxWidth) {
            chunk.setLength(chunk.length() - 1);
            parts.add(chunk.toString());
            chunk.setLength(0);
            chunk.append(word.charAt(index));
          }
        }
        if (chunk.length() > 0) {
          parts.add(chunk.toString());
        }
      }
      return parts;
    }

    private float textWidth(PDType0Font font, float size, String text) throws IOException {
      return font.getStringWidth(text) / 1000f * size;
    }

    private String sanitize(PDType0Font font, String text) {
      StringBuilder builder = new StringBuilder();
      int index = 0;
      while (index < text.length()) {
        int codePoint = text.codePointAt(index);
        int charCount = Character.charCount(codePoint);
        String symbol = text.substring(index, index + charCount);
        if (codePoint >= 32) {
          try {
            font.getStringWidth(symbol);
            builder.append(symbol);
          } catch (IllegalArgumentException | IOException ex) {
            builder.append(' ');
          }
        }
        index += charCount;
      }
      return builder.toString();
    }
  }
}
