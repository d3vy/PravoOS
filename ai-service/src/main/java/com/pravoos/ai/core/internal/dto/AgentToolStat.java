package com.pravoos.ai.core.internal.dto;

public record AgentToolStat(
    String toolName, long created, long approved, long rejected, long expired, long failed) {}
