package com.pravoos.ai.document.internal.pipeline;

public record ChunkData(String content, int index, float[] embedding) {}
