package com.pravoos.ai.practice.internal.dto;

public record ExportedFile(
        byte[] content,
        String fileName,
        String contentType
) {}
