package com.pravoos.llm.web.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

public record EmbedBatchRequest(@NotNull List<String> texts) {}
