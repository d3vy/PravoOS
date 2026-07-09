package com.pravoos.llm.web.dto;

import jakarta.validation.constraints.NotNull;

public record EmbedRequest(@NotNull String text) {}
