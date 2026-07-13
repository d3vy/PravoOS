package com.pravoos.common.web;

import java.util.List;
import java.util.UUID;

public record OrgContext(List<UUID> orgIds, List<UUID> clientIds, PlanLimits planLimits) {

    public OrgContext {
        orgIds = orgIds == null ? List.of() : List.copyOf(orgIds);
        clientIds = clientIds == null ? List.of() : List.copyOf(clientIds);
    }

    public OrgContext(List<UUID> orgIds) {
        this(orgIds, List.of(), null);
    }

    public OrgContext(List<UUID> orgIds, List<UUID> clientIds) {
        this(orgIds, clientIds, null);
    }
}
