package com.pravoos.ai.exception;

import org.springframework.http.HttpStatus;

import java.util.UUID;

public class OrgMembershipCheckException extends PravoosException {

    public OrgMembershipCheckException(UUID orgId) {
        super("Не удалось проверить членство в организации " + orgId + ", попробуйте позже",
                HttpStatus.SERVICE_UNAVAILABLE, "ORG_MEMBERSHIP_CHECK_FAILED");
    }
}
