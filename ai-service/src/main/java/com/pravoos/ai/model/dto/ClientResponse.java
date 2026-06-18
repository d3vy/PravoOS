package com.pravoos.ai.model.dto;

import com.pravoos.ai.model.entity.Client;
import com.pravoos.ai.model.enums.ClientType;

import java.time.LocalDateTime;
import java.util.UUID;

public record ClientResponse(
        UUID id,
        String name,
        ClientType type,
        String typeName,
        String phone,
        String email,
        String inn,
        String notes,
        LocalDateTime createdAt,
        long caseCount
) {
    public static ClientResponse from(Client client, long caseCount) {
        return new ClientResponse(
                client.getId(),
                client.getName(),
                client.getType(),
                client.getType().getDisplayName(),
                client.getPhone(),
                client.getEmail(),
                client.getInn(),
                client.getNotes(),
                client.getCreatedAt(),
                caseCount
        );
    }
}
