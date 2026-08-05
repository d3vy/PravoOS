package com.pravoos.user.identity.api;

import java.util.List;
import java.util.UUID;

public interface UserSessionQuery {

  List<UserSessionSnapshot> activeSessions(UUID userId);
}
