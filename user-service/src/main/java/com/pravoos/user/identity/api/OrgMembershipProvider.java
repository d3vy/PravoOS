package com.pravoos.user.identity.api;

import java.util.List;
import java.util.UUID;

public interface OrgMembershipProvider {

  List<UUID> orgIdsForUser(UUID userId);
}
