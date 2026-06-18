package com.pravoos.ai.model.dto;

import java.util.List;

public record ClientDetailResponse(
        ClientResponse client,
        List<CaseResponse> cases
) {}
