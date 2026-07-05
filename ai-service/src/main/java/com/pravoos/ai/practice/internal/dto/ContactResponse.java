package com.pravoos.ai.practice.internal.dto;

import com.pravoos.ai.practice.internal.model.entity.ClientContact;
import com.pravoos.ai.shared.model.enums.ContactType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record ContactResponse(
        UUID id,
        UUID clientId,
        ContactType type,
        String typeName,
        LocalDate contactDate,
        String notes,
        LocalDateTime createdAt
) {
    public static ContactResponse from(ClientContact contact) {
        return new ContactResponse(
                contact.getId(),
                contact.getClientId(),
                contact.getType(),
                contact.getType().getDisplayName(),
                contact.getContactDate(),
                contact.getNotes(),
                contact.getCreatedAt()
        );
    }
}
