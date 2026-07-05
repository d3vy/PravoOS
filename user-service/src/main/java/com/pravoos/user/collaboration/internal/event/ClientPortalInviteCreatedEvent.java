package com.pravoos.user.collaboration.internal.event;

public record ClientPortalInviteCreatedEvent(String email, String clientName, String rawToken) {}
