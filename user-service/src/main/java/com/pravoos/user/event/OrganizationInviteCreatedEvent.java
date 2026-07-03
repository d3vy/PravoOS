package com.pravoos.user.event;

public record OrganizationInviteCreatedEvent(String email, String organizationName, String inviterName, String rawToken) {}
