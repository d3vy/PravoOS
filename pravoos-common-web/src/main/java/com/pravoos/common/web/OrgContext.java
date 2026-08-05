package com.pravoos.common.web;

import java.util.List;
import java.util.UUID;

public record OrgContext(
    List<UUID> orgIds,
    List<UUID> clientIds,
    PlanLimits planLimits,
    AiProcessingMode aiProcessingMode) {

  public OrgContext {
    orgIds = orgIds == null ? List.of() : List.copyOf(orgIds);
    clientIds = clientIds == null ? List.of() : List.copyOf(clientIds);
    aiProcessingMode = aiProcessingMode == null ? AiProcessingMode.DEFAULT : aiProcessingMode;
  }

  public OrgContext(List<UUID> orgIds) {
    this(orgIds, List.of(), null, AiProcessingMode.DEFAULT);
  }

  public OrgContext(List<UUID> orgIds, List<UUID> clientIds) {
    this(orgIds, clientIds, null, AiProcessingMode.DEFAULT);
  }

  public OrgContext(List<UUID> orgIds, List<UUID> clientIds, PlanLimits planLimits) {
    this(orgIds, clientIds, planLimits, AiProcessingMode.DEFAULT);
  }
}
