package com.pravoos.user.collaboration.internal.dto;

import com.pravoos.user.collaboration.internal.model.enums.OrgRole;
import jakarta.validation.constraints.NotNull;

public record ChangeMemberRoleRequest(@NotNull(message = "Роль обязательна") OrgRole orgRole) {}
