package com.pravoos.common.web;

import java.util.List;
import java.util.UUID;

public record OrgContext(List<UUID> orgIds) {

    public OrgContext {
        orgIds = orgIds == null ? List.of() : List.copyOf(orgIds);
    }
}
