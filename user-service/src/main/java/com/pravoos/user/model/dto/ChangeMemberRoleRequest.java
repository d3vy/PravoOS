package com.pravoos.user.model.dto;

import com.pravoos.user.model.enums.OrgRole;
import jakarta.validation.constraints.NotNull;

public record ChangeMemberRoleRequest(
        @NotNull(message = "Роль обязательна")
        OrgRole orgRole
) {
}
