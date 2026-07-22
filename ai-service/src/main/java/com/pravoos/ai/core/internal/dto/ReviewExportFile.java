package com.pravoos.ai.core.internal.dto;

public record ReviewExportFile(
        byte[] content,
        String fileName,
        String contentType) {}
