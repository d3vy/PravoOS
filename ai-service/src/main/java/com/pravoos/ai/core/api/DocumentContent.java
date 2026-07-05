package com.pravoos.ai.core.api;

import org.springframework.core.io.Resource;

public record DocumentContent(Resource resource, String fileName, String fileType, long contentLength) {
}
