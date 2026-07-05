package com.pravoos.ai.core.internal.pipeline;

public record ChunkData(String content, int index, float[] embedding) {}
