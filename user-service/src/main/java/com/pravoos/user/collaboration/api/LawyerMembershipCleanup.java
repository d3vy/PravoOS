package com.pravoos.user.collaboration.api;

import java.util.Map;
import java.util.UUID;

public interface LawyerMembershipCleanup {

  Map<UUID, UUID> purgeMembershipsForDeletedLawyer(UUID userId);
}
