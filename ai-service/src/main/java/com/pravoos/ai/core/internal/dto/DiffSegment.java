package com.pravoos.ai.core.internal.dto;

import com.pravoos.ai.shared.model.enums.DiffSegmentType;

public record DiffSegment(DiffSegmentType type, String text) {}
