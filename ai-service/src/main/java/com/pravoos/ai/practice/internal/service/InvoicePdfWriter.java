package com.pravoos.ai.practice.internal.service;

import com.pravoos.ai.practice.internal.dto.InvoiceExportModel;
import com.pravoos.ai.practice.internal.dto.InvoiceExportModel.ClientBlock;
import com.pravoos.ai.practice.internal.dto.InvoiceExportModel.LineRow;
import com.pravoos.ai.practice.internal.dto.InvoiceExportModel.SupplierBlock;
import com.pravoos.ai.shared.exception.CaseExportException;
import com.pravoos.ai.shared.util.ExportDateFormatter;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.springframework.stereotype.Component;

@Component
public class InvoicePdfWriter {

  private static final String REGULAR_FONT = "/fonts/DejaVuSans.ttf";
  private static final String BOLD_FONT = "/fonts/DejaVuSans-Bold.ttf";

  private static final float MARGIN = 50f;
  private static final float PAGE_WIDTH = PDRectangle.A4.getWidth();
  private static final float PAGE_HEIGHT = PDRectangle.A4.getHeight();
  private static final float CONTENT_WIDTH = PAGE_WIDTH - 2 * MARGIN;

  private static final float COL_NUM = 26f;
  private static final float COL_MINUTES = 70f;
  private static final float COL_RATE = 80f;
  private static final float COL_AMOUNT = 90f;
  private static final float COL_DESC =
      CONTENT_WIDTH - COL_NUM - COL_MINUTES - COL_RATE - COL_AMOUNT;

  private static final Color HEADER_BG = new Color(0.93f, 0.94f, 0.96f);
  private static final Color BORDER = new Color(0.80f, 0.82f, 0.85f);
  private static final Color MUTED = new Color(0.45f, 0.47f, 0.5f);

  public byte[] write(InvoiceExportModel model) {
    try (PDDocument document = new PDDocument();
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {

      PDType0Font regular = loadFont(document, REGULAR_FONT);
      PDType0Font bold = loadFont(document, BOLD_FONT);
      Renderer renderer = new Renderer(document, regular, bold);

      renderer.title("Счёт № " + model.number());
      renderer.muted(headerMeta(model));
      renderer.gap(10f);

      if (model.supplier() != null) {
        renderSupplier(renderer, model.supplier());
        renderer.gap(8f);
      }

      renderClient(renderer, model.client());
      renderer.gap(8f);

      renderTable(renderer, model);
      renderer.gap(6f);
      renderTotals(renderer, model);

      if (isPresent(model.notes())) {
        renderer.gap(12f);
        renderer.heading("Примечания");
        renderer.body(model.notes());
      }

      renderer.finish();
      document.save(outputStream);
      return outputStream.toByteArray();
    } catch (IOException ex) {
      throw new CaseExportException("Failed to generate invoice .pdf", ex);
    }
  }

  private String headerMeta(InvoiceExportModel model) {
    StringBuilder builder = new StringBuilder();
    builder.append("Статус: ").append(model.statusLabel());
    builder.append("  ·  Дата: ").append(ExportDateFormatter.formatDate(model.issueDate()));
    if (model.dueDate() != null) {
      builder.append("  ·  Оплатить до: ").append(ExportDateFormatter.formatDate(model.dueDate()));
    }
    return builder.toString();
  }

  private void renderSupplier(Renderer renderer, SupplierBlock supplier) throws IOException {
    renderer.heading("Исполнитель");
    renderer.labeled("Наименование", supplier.name());
    labelIfPresent(renderer, "ИНН", supplier.inn());
    labelIfPresent(renderer, "КПП", supplier.kpp());
    labelIfPresent(renderer, "ОГРН/ОГРНИП", supplier.ogrn());
    labelIfPresent(renderer, "Адрес", supplier.legalAddress());
    labelIfPresent(renderer, "Банк", supplier.bankName());
    labelIfPresent(renderer, "БИК", supplier.bankBic());
    labelIfPresent(renderer, "Расчётный счёт", supplier.bankAccount());
    labelIfPresent(renderer, "Корр. счёт", supplier.corrAccount());
    labelIfPresent(renderer, "Email", supplier.email());
    labelIfPresent(renderer, "Телефон", supplier.phone());
  }

  private void labelIfPresent(Renderer renderer, String label, String value) throws IOException {
    if (isPresent(value)) {
      renderer.labeled(label, value);
    }
  }

  private void renderClient(Renderer renderer, ClientBlock client) throws IOException {
    renderer.heading("Плательщик");
    renderer.labeled("Наименование", client.name());
    renderer.labeled("Тип", client.type());
    if (isPresent(client.inn())) {
      renderer.labeled("ИНН", client.inn());
    }
    if (isPresent(client.email())) {
      renderer.labeled("Email", client.email());
    }
    if (isPresent(client.phone())) {
      renderer.labeled("Телефон", client.phone());
    }
  }

  private void renderTable(Renderer renderer, InvoiceExportModel model) throws IOException {
    renderer.tableHeader();
    int index = 1;
    for (LineRow line : model.lines()) {
      renderer.tableRow(
          String.valueOf(index++),
          line.description(),
          formatHours(line.minutes()),
          formatMoney(line.hourlyRate()),
          formatMoney(line.amount()));
    }
  }

  private void renderTotals(Renderer renderer, InvoiceExportModel model) throws IOException {
    renderer.totalLine("Всего времени", formatHours(model.totalMinutes()));
    renderer.totalLine("Сумма", formatMoney(model.subtotal()) + " " + model.currency());
    if (model.vatRate() != null && model.vatRate().signum() > 0) {
      renderer.totalLine(
          "НДС " + formatRate(model.vatRate()) + "%",
          formatMoney(model.vatAmount()) + " " + model.currency());
    } else {
      renderer.totalLine("НДС", "не облагается");
    }
    renderer.grandTotal("Итого к оплате", formatMoney(model.total()) + " " + model.currency());
  }

  private String formatHours(int minutes) {
    int hours = minutes / 60;
    int rest = minutes % 60;
    if (hours == 0) {
      return rest + " мин";
    }
    if (rest == 0) {
      return hours + " ч";
    }
    return hours + " ч " + rest + " мин";
  }

  private String formatRate(BigDecimal rate) {
    return rate.stripTrailingZeros().toPlainString();
  }

  private String formatMoney(BigDecimal amount) {
    DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.forLanguageTag("ru-RU"));
    symbols.setGroupingSeparator(' ');
    symbols.setDecimalSeparator(',');
    DecimalFormat format = new DecimalFormat("#,##0.00", symbols);
    return format.format(amount);
  }

  private PDType0Font loadFont(PDDocument document, String resourcePath) throws IOException {
    try (InputStream fontStream = getClass().getResourceAsStream(resourcePath)) {
      if (fontStream == null) {
        throw new IOException("Embedded font not found on classpath: " + resourcePath);
      }
      return PDType0Font.load(document, fontStream);
    }
  }

  private boolean isPresent(String value) {
    return value != null && !value.isBlank();
  }

  private static final class Renderer {

    private final PDDocument document;
    private final PDType0Font regular;
    private final PDType0Font bold;
    private PDPage page;
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
      drawText(sanitize(bold, text), bold, 20f, MARGIN, cursorY, Color.BLACK);
      cursorY -= 26f;
    }

    void heading(String text) throws IOException {
      ensureSpace(20f);
      drawText(sanitize(bold, text), bold, 13f, MARGIN, cursorY, Color.BLACK);
      cursorY -= 18f;
    }

    void muted(String text) throws IOException {
      paragraph(text, regular, 10f, MUTED);
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

    void tableHeader() throws IOException {
      float rowHeight = 20f;
      ensureSpace(rowHeight);
      float top = cursorY;
      fillRect(MARGIN, top - rowHeight, CONTENT_WIDTH, rowHeight, HEADER_BG);
      drawRowBorder(top, rowHeight);
      float textY = top - 14f;
      drawText("№", bold, 9.5f, MARGIN + 4f, textY, Color.BLACK);
      drawText("Описание работ", bold, 9.5f, MARGIN + COL_NUM + 4f, textY, Color.BLACK);
      drawRight("Время", bold, 9.5f, MARGIN + COL_NUM + COL_DESC + COL_MINUTES - 4f, textY);
      drawRight(
          "Ставка", bold, 9.5f, MARGIN + COL_NUM + COL_DESC + COL_MINUTES + COL_RATE - 4f, textY);
      drawRight("Сумма", bold, 9.5f, MARGIN + CONTENT_WIDTH - 4f, textY);
      cursorY -= rowHeight;
    }

    void tableRow(String num, String description, String minutes, String rate, String amount)
        throws IOException {
      float fontSize = 10f;
      List<String> descLines =
          wrap(regular, fontSize, sanitize(regular, description), COL_DESC - 8f);
      float rowHeight = Math.max(18f, descLines.size() * fontSize * 1.3f + 6f);
      if (cursorY - rowHeight < MARGIN) {
        newPage();
        tableHeader();
      }
      float top = cursorY;
      drawRowBorder(top, rowHeight);
      float textY = top - 13f;
      drawText(num, regular, fontSize, MARGIN + 4f, textY, Color.BLACK);
      float descY = textY;
      for (String descLine : descLines) {
        drawText(descLine, regular, fontSize, MARGIN + COL_NUM + 4f, descY, Color.BLACK);
        descY -= fontSize * 1.3f;
      }
      drawRight(minutes, regular, fontSize, MARGIN + COL_NUM + COL_DESC + COL_MINUTES - 4f, textY);
      drawRight(
          rate,
          regular,
          fontSize,
          MARGIN + COL_NUM + COL_DESC + COL_MINUTES + COL_RATE - 4f,
          textY);
      drawRight(amount, regular, fontSize, MARGIN + CONTENT_WIDTH - 4f, textY);
      cursorY -= rowHeight;
    }

    void totalLine(String label, String value) throws IOException {
      ensureSpace(16f);
      drawRight(
          sanitize(regular, label + ":"),
          regular,
          11f,
          MARGIN + CONTENT_WIDTH - 120f,
          cursorY - 12f);
      drawRight(sanitize(regular, value), regular, 11f, MARGIN + CONTENT_WIDTH - 4f, cursorY - 12f);
      cursorY -= 16f;
    }

    void grandTotal(String label, String value) throws IOException {
      ensureSpace(22f);
      drawRight(
          sanitize(bold, label + ":"), bold, 13f, MARGIN + CONTENT_WIDTH - 150f, cursorY - 14f);
      drawRight(sanitize(bold, value), bold, 13f, MARGIN + CONTENT_WIDTH - 4f, cursorY - 14f);
      cursorY -= 22f;
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

    private void drawRight(String text, PDType0Font font, float size, float rightX, float y)
        throws IOException {
      String safe = sanitize(font, text);
      float width = font.getStringWidth(safe) / 1000f * size;
      drawText(safe, font, size, rightX - width, y, Color.BLACK);
    }

    private void fillRect(float x, float y, float width, float height, Color color)
        throws IOException {
      content.setNonStrokingColor(color);
      content.addRect(x, y, width, height);
      content.fill();
    }

    private void drawRowBorder(float top, float height) throws IOException {
      content.setStrokingColor(BORDER);
      content.setLineWidth(0.5f);
      content.addRect(MARGIN, top - height, CONTENT_WIDTH, height);
      content.stroke();
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
      page = new PDPage(PDRectangle.A4);
      document.addPage(page);
      content = new PDPageContentStream(document, page);
      cursorY = PAGE_HEIGHT - MARGIN;
    }

    void finish() throws IOException {
      if (content != null) {
        content.close();
        content = null;
      }
    }

    private List<String> wrap(PDType0Font font, float size, String text, float maxWidth)
        throws IOException {
      List<String> lines = new ArrayList<>();
      StringBuilder current = new StringBuilder();
      for (String word : text.split(" ", -1)) {
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
