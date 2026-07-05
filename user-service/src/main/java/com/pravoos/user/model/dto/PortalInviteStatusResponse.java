package com.pravoos.user.model.dto;

import com.pravoos.user.model.enums.PortalAccessStatus;

import java.time.LocalDateTime;

public record PortalInviteStatusResponse(PortalAccessStatus status, String email, LocalDateTime expiresAt) {

    public static PortalInviteStatusResponse none() {
        return new PortalInviteStatusResponse(PortalAccessStatus.NONE, null, null);
    }

    public static PortalInviteStatusResponse pending(String email, LocalDateTime expiresAt) {
        return new PortalInviteStatusResponse(PortalAccessStatus.PENDING, email, expiresAt);
    }

    public static PortalInviteStatusResponse accepted() {
        return new PortalInviteStatusResponse(PortalAccessStatus.ACCEPTED, null, null);
    }
}
