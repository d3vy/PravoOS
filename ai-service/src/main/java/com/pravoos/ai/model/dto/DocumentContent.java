package com.pravoos.ai.model.dto;

import org.springframework.core.io.Resource;

public record DocumentContent(Resource resource, String fileName, String fileType, long contentLength) {
}
