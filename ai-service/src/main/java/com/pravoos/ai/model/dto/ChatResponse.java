package com.pravoos.ai.model.dto;

import java.util.List;

public record ChatResponse(
        String conversationId,
        String answer,
        List<String> sources,
        List<String> followUps
) {}
