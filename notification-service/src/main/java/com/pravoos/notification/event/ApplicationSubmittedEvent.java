package com.pravoos.notification.event;

import java.util.UUID;

public record ApplicationSubmittedEvent(
        UUID applicationId
) {}
