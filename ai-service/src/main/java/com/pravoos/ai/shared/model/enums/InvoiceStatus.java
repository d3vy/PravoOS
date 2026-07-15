package com.pravoos.ai.shared.model.enums;

import java.util.Set;

public enum InvoiceStatus {

    DRAFT("Черновик"),
    ISSUED("Выставлен"),
    PAID("Оплачен"),
    CANCELED("Отменён");

    private final String displayName;

    InvoiceStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean canTransitionTo(InvoiceStatus target) {
        return switch (this) {
            case DRAFT -> Set.of(ISSUED, CANCELED).contains(target);
            case ISSUED -> Set.of(PAID, CANCELED).contains(target);
            case PAID, CANCELED -> false;
        };
    }

    public boolean releasesTimeEntries() {
        return this == CANCELED;
    }
}
