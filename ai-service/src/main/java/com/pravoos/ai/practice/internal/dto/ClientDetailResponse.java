package com.pravoos.ai.practice.internal.dto;

import java.util.List;

public record ClientDetailResponse(
        ClientResponse client,
        List<CaseResponse> cases
) {}
