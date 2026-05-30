package com.pravoos.ai.pipeline;

import com.pravoos.ai.exception.DocumentProcessingException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Component;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;

@Component
public class DocumentParser {

    public String extractText(Path filePath, String fileType) {
        return switch (fileType.toLowerCase()) {
            case "pdf" -> extractPdfText(filePath);
            case "docx" -> extractDocxText(filePath);
            default -> throw new DocumentProcessingException("Unsupported file type: " + fileType);
        };
    }

    private String extractPdfText(Path filePath) {
        try (PDDocument document = Loader.loadPDF(filePath.toFile())) {
            return new PDFTextStripper().getText(document);
        } catch (IOException e) {
            throw new DocumentProcessingException("Failed to parse PDF: " + e.getMessage());
        }
    }

    private String extractDocxText(Path filePath) {
        try (FileInputStream fis = new FileInputStream(filePath.toFile());
             XWPFDocument document = new XWPFDocument(fis);
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            return extractor.getText();
        } catch (IOException e) {
            throw new DocumentProcessingException("Failed to parse DOCX: " + e.getMessage());
        }
    }
}
