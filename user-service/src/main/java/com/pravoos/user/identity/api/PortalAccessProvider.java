package com.pravoos.user.identity.api;

import java.util.List;
import java.util.UUID;

public interface PortalAccessProvider {

    List<UUID> acceptedClientIdsForUser(UUID userId);
}
