package com.pravoos.ai.document.internal.pipeline;

import com.pravoos.ai.shared.exception.DocumentProcessingException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
public class DocumentParser {

    public String extractText(byte[] content, String fileType) {
        return switch (fileType.toLowerCase()) {
            case "pdf" -> extractPdfText(content);
            case "docx" -> extractDocxText(content);
            case "txt" -> extractPlainText(content);
            default -> throw new DocumentProcessingException("Unsupported file type: " + fileType);
        };
    }

    private String extractPlainText(byte[] content) {
        return new String(content, StandardCharsets.UTF_8);
    }

    private String extractPdfText(byte[] content) {
        try (PDDocument document = Loader.loadPDF(content)) {
            return new PDFTextStripper().getText(document);
        } catch (IOException e) {
            throw new DocumentProcessingException("Failed to parse PDF: " + e.getMessage());
        }
    }

    private String extractDocxText(byte[] content) {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(content));
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            return extractor.getText();
        } catch (IOException e) {
            throw new DocumentProcessingException("Failed to parse DOCX: " + e.getMessage());
        }
    }
}
