package com.pravoos.ai.practice.internal.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.pravoos.ai.practice.internal.dto.InvoiceExportModel;
import com.pravoos.ai.practice.internal.dto.InvoiceExportModel.ClientBlock;
import com.pravoos.ai.practice.internal.dto.InvoiceExportModel.LineRow;
import com.pravoos.ai.practice.internal.dto.InvoiceExportModel.SupplierBlock;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

class InvoicePdfWriterTest {

  private final InvoicePdfWriter writer = new InvoicePdfWriter();

  private ClientBlock client() {
    return new ClientBlock("ООО Ромашка", "Организация", "7701234567", "client@example.com", null);
  }

  private LineRow line(String description, int minutes, String rate, String amount) {
    return new LineRow(description, minutes, new BigDecimal(rate), new BigDecimal(amount));
  }

  private InvoiceExportModel baseModel(
      SupplierBlock supplier, BigDecimal vatRate, BigDecimal vatAmount, String notes) {
    return new InvoiceExportModel(
        "СЧ-2026-0001",
        "Выставлен",
        LocalDate.of(2026, 8, 1),
        LocalDate.of(2026, 8, 15),
        "RUB",
        supplier,
        client(),
        List.of(line("Подготовка иска", 90, "3000", "4500")),
        90,
        new BigDecimal("4500.00"),
        vatRate,
        vatAmount,
        vatRate != null ? new BigDecimal("5400.00") : new BigDecimal("4500.00"),
        notes);
  }

  private String extractText(byte[] pdfBytes) throws IOException {
    try (PDDocument document = Loader.loadPDF(pdfBytes)) {
      return new PDFTextStripper().getText(document);
    }
  }

  @Test
  void generatesValidPdfWithMinimalModel() throws IOException {
    InvoiceExportModel model = baseModel(null, null, null, null);

    byte[] pdf = writer.write(model);

    assertThat(pdf).isNotEmpty();
    String text = extractText(pdf);
    assertThat(text).contains("Счёт № СЧ-2026-0001");
    assertThat(text).contains("Плательщик");
    assertThat(text).contains("ООО Ромашка");
    assertThat(text).doesNotContain("Исполнитель");
    assertThat(text).contains("не облагается");
  }

  @Test
  void includesSupplierBlockWhenPresent() throws IOException {
    SupplierBlock supplier =
        new SupplierBlock(
            "ИП Иванов",
            "770123456789",
            null,
            "312770000000000",
            "г. Москва",
            "Тинькофф",
            "044525974",
            "40802810000000000000",
            "30101810000000000000",
            "ivanov@example.com",
            "+7 999 000-00-00");
    InvoiceExportModel model = baseModel(supplier, null, null, null);

    String text = extractText(writer.write(model));

    assertThat(text).contains("Исполнитель");
    assertThat(text).contains("ИП Иванов");
    assertThat(text).contains("БИК");
  }

  @Test
  void includesVatLineWhenVatRatePositive() throws IOException {
    InvoiceExportModel model =
        baseModel(null, new BigDecimal("20"), new BigDecimal("900.00"), null);

    String text = extractText(writer.write(model));

    assertThat(text).contains("НДС 20%");
    assertThat(text).doesNotContain("не облагается");
  }

  @Test
  void includesNotesSectionWhenPresent() throws IOException {
    InvoiceExportModel model = baseModel(null, null, null, "Оплата в течение 10 дней");

    String text = extractText(writer.write(model));

    assertThat(text).contains("Примечания");
    assertThat(text).contains("Оплата в течение 10 дней");
  }

  @Test
  void omitsNotesSectionWhenBlank() throws IOException {
    InvoiceExportModel model = baseModel(null, null, null, "   ");

    String text = extractText(writer.write(model));

    assertThat(text).doesNotContain("Примечания");
  }

  @Test
  void paginatesWhenManyLinesExceedSinglePage() throws IOException {
    List<LineRow> lines = new ArrayList<>();
    for (int i = 0; i < 80; i++) {
      lines.add(line("Работа по делу №" + i, 30, "3000", "1500"));
    }
    InvoiceExportModel model =
        new InvoiceExportModel(
            "СЧ-2026-0002",
            "Выставлен",
            LocalDate.of(2026, 8, 1),
            LocalDate.of(2026, 8, 15),
            "RUB",
            null,
            client(),
            lines,
            2400,
            new BigDecimal("120000.00"),
            null,
            null,
            new BigDecimal("120000.00"),
            null);

    byte[] pdf = writer.write(model);

    try (PDDocument document = Loader.loadPDF(pdf)) {
      assertThat(document.getNumberOfPages()).isGreaterThan(1);
    }
  }
}
