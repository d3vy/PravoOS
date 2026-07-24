package com.pravoos.user.collaboration.internal.event;

public record OrganizationInviteCreatedEvent(
    String email, String organizationName, String inviterName, String rawToken) {}
