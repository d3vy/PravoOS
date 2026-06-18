package com.pravoos.ai.model.enums;

import com.pravoos.ai.exception.InvalidExportFormatException;

public enum ExportFormat {

    DOCX("application/vnd.openxmlformats-officedocument.wordprocessingml.document", "docx"),
    PDF("application/pdf", "pdf");

    private final String contentType;
    private final String extension;

    ExportFormat(String contentType, String extension) {
        this.contentType = contentType;
        this.extension = extension;
    }

    public String contentType() {
        return contentType;
    }

    public String extension() {
        return extension;
    }

    public static ExportFormat parse(String value) {
        if (value == null || value.isBlank()) {
            return DOCX;
        }
        try {
            return ExportFormat.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new InvalidExportFormatException(value);
        }
    }
}
