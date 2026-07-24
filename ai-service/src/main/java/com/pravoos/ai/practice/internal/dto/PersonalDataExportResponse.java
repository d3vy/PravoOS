package com.pravoos.ai.practice.internal.dto;

import com.pravoos.ai.practice.internal.model.entity.Client;
import com.pravoos.ai.shared.model.enums.ClientType;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

public record PersonalDataExportResponse(
        UUID clientId,
        String name,
        ClientType type,
        String phone,
        String email,
        String inn,
        String notes,
        LocalDateTime createdAt,
        List<ConsentResponse> consents,
        List<CaseResponse> cases,
        LocalDateTime exportedAt
) {
    public static PersonalDataExportResponse of(Client client,
                                                List<ConsentResponse> consents,
                                                List<CaseResponse> cases) {
        return new PersonalDataExportResponse(
                client.getId(),
                client.getName(),
                client.getType(),
                client.getPhone(),
                client.getEmail(),
                client.getInn(),
                client.getNotes(),
                client.getCreatedAt(),
                consents,
                cases,
                LocalDateTime.now(ZoneOffset.UTC)
        );
    }
}
