package com.pravoos.ai.model.dto;

public record ExportedFile(
        byte[] content,
        String fileName,
        String contentType
) {}
