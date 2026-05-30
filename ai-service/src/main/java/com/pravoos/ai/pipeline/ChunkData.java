package com.pravoos.ai.pipeline;

public record ChunkData(String content, int index, float[] embedding) {}
