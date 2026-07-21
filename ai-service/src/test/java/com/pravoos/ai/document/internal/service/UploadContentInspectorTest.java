package com.pravoos.ai.document.internal.service;

import com.pravoos.ai.shared.config.UploadGuardProperties;
import com.pravoos.ai.shared.exception.FileTooLargeException;
import com.pravoos.ai.shared.exception.UnsafeFileContentException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UploadContentInspectorTest {

    private UploadContentInspector inspector(UploadGuardProperties properties) {
        return new UploadContentInspector(properties, new SimpleMeterRegistry());
    }

    private UploadGuardProperties defaults() {
        return new UploadGuardProperties(1024 * 1024, 8 * 1024 * 1024, 200, 100, true);
    }

    private byte[] zip(Map<String, byte[]> entries) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue());
                zip.closeEntry();
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return out.toByteArray();
    }

    private byte[] docx(String... extraEntries) {
        var entries = new java.util.LinkedHashMap<String, byte[]>();
        entries.put("[Content_Types].xml", "<Types/>".getBytes(StandardCharsets.UTF_8));
        entries.put("word/document.xml", "<w:document/>".getBytes(StandardCharsets.UTF_8));
        for (String extra : extraEntries) {
            entries.put(extra, "payload".getBytes(StandardCharsets.UTF_8));
        }
        return zip(entries);
    }

    @Test
    void rejectsFileOverSizeLimit() {
        assertThatThrownBy(() -> inspector(defaults()).assertWithinSizeLimit(2 * 1024 * 1024, "big.pdf"))
                .isInstanceOf(FileTooLargeException.class);
    }

    @Test
    void acceptsFileWithinSizeLimit() {
        assertThatCode(() -> inspector(defaults()).assertWithinSizeLimit(1024, "small.pdf"))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsPdfWithJavaScript() {
        byte[] content = "%PDF-1.7 /OpenAction << /S /JavaScript /JS (app.alert) >>".getBytes(StandardCharsets.US_ASCII);

        assertThatThrownBy(() -> inspector(defaults()).inspect(content, "pdf", "malicious.pdf"))
                .isInstanceOf(UnsafeFileContentException.class)
                .hasMessageContaining("активное содержимое");
    }

    @Test
    void acceptsPlainPdf() {
        byte[] content = "%PDF-1.7 /Type /Page /OpenAction << /S /GoTo >>".getBytes(StandardCharsets.US_ASCII);

        assertThatCode(() -> inspector(defaults()).inspect(content, "pdf", "contract.pdf"))
                .doesNotThrowAnyException();
    }

    @Test
    void skipsActiveContentChecksWhenDisabled() {
        UploadGuardProperties disabled = new UploadGuardProperties(1024 * 1024, 8 * 1024 * 1024, 200, 100, false);
        byte[] content = "%PDF-1.7 /JavaScript".getBytes(StandardCharsets.US_ASCII);

        assertThatCode(() -> inspector(disabled).inspect(content, "pdf", "contract.pdf"))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsDocxWithMacros() {
        byte[] content = docx("word/vbaProject.bin");

        assertThatThrownBy(() -> inspector(defaults()).inspect(content, "docx", "macro.docx"))
                .isInstanceOf(UnsafeFileContentException.class)
                .hasMessageContaining("макросы");
    }

    @Test
    void rejectsDocxWithExecutableEntry() {
        byte[] content = docx("word/embeddings/payload.exe");

        assertThatThrownBy(() -> inspector(defaults()).inspect(content, "docx", "dropper.docx"))
                .isInstanceOf(UnsafeFileContentException.class)
                .hasMessageContaining("исполняемое вложение");
    }

    @Test
    void rejectsDocxWithPathTraversalEntry() {
        byte[] content = docx("../../etc/passwd");

        assertThatThrownBy(() -> inspector(defaults()).inspect(content, "docx", "traversal.docx"))
                .isInstanceOf(UnsafeFileContentException.class)
                .hasMessageContaining("недопустимым путём");
    }

    @Test
    void rejectsZipBombByCompressionRatio() {
        byte[] highlyCompressible = new byte[4 * 1024 * 1024];
        byte[] content = zip(Map.of("[Content_Types].xml", highlyCompressible));
        UploadGuardProperties strictRatio = new UploadGuardProperties(64 * 1024 * 1024, 0, 10, 100, true);

        assertThatThrownBy(() -> inspector(strictRatio).inspect(content, "docx", "bomb.docx"))
                .isInstanceOf(UnsafeFileContentException.class)
                .hasMessageContaining("сжатия");
    }

    @Test
    void rejectsDocxOverUncompressedLimit() {
        byte[] payload = new byte[512 * 1024];
        byte[] content = zip(Map.of("[Content_Types].xml", payload));
        UploadGuardProperties strictSize = new UploadGuardProperties(64 * 1024 * 1024, 64 * 1024, 0, 100, true);

        assertThatThrownBy(() -> inspector(strictSize).inspect(content, "docx", "bomb.docx"))
                .isInstanceOf(UnsafeFileContentException.class)
                .hasMessageContaining("распакованный размер");
    }

    @Test
    void acceptsPlainDocx() {
        assertThatCode(() -> inspector(defaults()).inspect(docx(), "docx", "claim.docx"))
                .doesNotThrowAnyException();
    }
}
