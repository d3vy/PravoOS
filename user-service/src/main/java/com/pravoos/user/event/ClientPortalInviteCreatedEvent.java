package com.pravoos.user.event;

public record ClientPortalInviteCreatedEvent(String email, String clientName, String rawToken) {}
