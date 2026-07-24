package com.pravoos.ai.document.internal.service;

import com.pravoos.ai.shared.config.UploadGuardProperties;
import com.pravoos.ai.shared.exception.DocumentProcessingException;
import com.pravoos.ai.shared.exception.FileTooLargeException;
import com.pravoos.ai.shared.exception.UnsafeFileContentException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class UploadContentInspector {

  private static final Logger log = LoggerFactory.getLogger(UploadContentInspector.class);

  private static final List<String> PDF_ACTIVE_CONTENT_MARKERS =
      List.of("/JavaScript", "/Launch", "/EmbeddedFile", "/RichMedia");
  private static final String MACRO_ENTRY = "vbaproject.bin";
  private static final Set<String> EXECUTABLE_ENTRY_EXTENSIONS =
      Set.of(
          "exe", "dll", "com", "scr", "bat", "cmd", "ps1", "vbs", "js", "jse", "wsf", "hta", "jar",
          "lnk", "msi");
  private static final int ZIP_READ_BUFFER = 8192;

  private final UploadGuardProperties properties;
  private final MeterRegistry meterRegistry;

  public UploadContentInspector(UploadGuardProperties properties, MeterRegistry meterRegistry) {
    this.properties = properties;
    this.meterRegistry = meterRegistry;
  }

  public void inspect(byte[] content, String fileType, String fileName) {
    assertWithinSizeLimit(content.length, fileName);
    if (!properties.blockActiveContent()) {
      return;
    }
    switch (fileType) {
      case "pdf" -> inspectPdf(content, fileName);
      case "docx" -> inspectZipContainer(content, fileName);
      default -> {}
    }
  }

  public void assertWithinSizeLimit(long sizeBytes, String fileName) {
    if (properties.maxFileBytes() > 0 && sizeBytes > properties.maxFileBytes()) {
      log.warn(
          "Upload rejected: '{}' is {} bytes, limit is {}",
          fileName,
          sizeBytes,
          properties.maxFileBytes());
      recordRejection("too-large");
      throw new FileTooLargeException(properties.maxFileBytes());
    }
  }

  private void inspectPdf(byte[] content, String fileName) {
    for (String marker : PDF_ACTIVE_CONTENT_MARKERS) {
      if (containsAscii(content, marker)) {
        reject(fileName, "active-content", "PDF содержит активное содержимое (" + marker + ")");
      }
    }
  }

  private void inspectZipContainer(byte[] content, String fileName) {
    long totalUncompressed = 0;
    int entryCount = 0;
    try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(content))) {
      byte[] buffer = new byte[ZIP_READ_BUFFER];
      ZipEntry entry;
      while ((entry = zip.getNextEntry()) != null) {
        if (++entryCount > properties.maxArchiveEntries()) {
          reject(fileName, "archive-entries", "документ содержит слишком много вложенных файлов");
        }
        assertSafeEntryName(entry.getName(), fileName);
        int read;
        while ((read = zip.read(buffer)) > 0) {
          totalUncompressed += read;
          assertNotZipBomb(totalUncompressed, content.length, fileName);
        }
      }
    } catch (IOException e) {
      throw new DocumentProcessingException("Failed to read uploaded file: " + e.getMessage());
    }
  }

  private void assertSafeEntryName(String entryName, String fileName) {
    String normalized = entryName.replace('\\', '/').toLowerCase(Locale.ROOT);
    if (normalized.startsWith("/") || normalized.contains("../")) {
      reject(fileName, "path-traversal", "документ содержит вложение с недопустимым путём");
    }
    if (normalized.endsWith(MACRO_ENTRY)) {
      reject(fileName, "macro", "документ содержит макросы (VBA)");
    }
    int dot = normalized.lastIndexOf('.');
    if (dot >= 0 && EXECUTABLE_ENTRY_EXTENSIONS.contains(normalized.substring(dot + 1))) {
      reject(fileName, "executable-entry", "документ содержит исполняемое вложение");
    }
  }

  private void assertNotZipBomb(long totalUncompressed, int compressedSize, String fileName) {
    if (properties.maxUncompressedBytes() > 0
        && totalUncompressed > properties.maxUncompressedBytes()) {
      reject(fileName, "zip-bomb", "распакованный размер документа превышает допустимый");
    }
    if (properties.maxCompressionRatio() > 0
        && totalUncompressed > (long) compressedSize * properties.maxCompressionRatio()) {
      reject(fileName, "zip-bomb", "документ имеет подозрительную степень сжатия");
    }
  }

  private boolean containsAscii(byte[] content, String marker) {
    byte[] pattern = marker.getBytes(StandardCharsets.US_ASCII);
    outer:
    for (int start = 0; start <= content.length - pattern.length; start++) {
      for (int offset = 0; offset < pattern.length; offset++) {
        if (content[start + offset] != pattern[offset]) {
          continue outer;
        }
      }
      return true;
    }
    return false;
  }

  private void reject(String fileName, String reasonTag, String message) {
    log.warn("Upload rejected by content inspector: '{}' — {}", fileName, message);
    recordRejection(reasonTag);
    throw new UnsafeFileContentException(message);
  }

  private void recordRejection(String reason) {
    Counter.builder("pravoos.document.upload.rejected")
        .description("Uploads rejected by the content inspector")
        .tag("reason", reason)
        .register(meterRegistry)
        .increment();
  }
}
