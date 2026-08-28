package com.pravoos.llm.web.dto;

import com.pravoos.llm.domain.LlmToolCall;
import java.util.List;

public record StreamToolCalls(List<LlmToolCall> toolCalls) {}
