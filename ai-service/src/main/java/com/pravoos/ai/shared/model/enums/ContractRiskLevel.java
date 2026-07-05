package com.pravoos.ai.shared.model.enums;

public enum ContractRiskLevel {

    HIGH(3, "Высокий"),
    MEDIUM(2, "Средний"),
    LOW(1, "Низкий");

    private final int weight;
    private final String displayName;

    ContractRiskLevel(int weight, String displayName) {
        this.weight = weight;
        this.displayName = displayName;
    }

    public int weight() {
        return weight;
    }

    public String displayName() {
        return displayName;
    }

    public static ContractRiskLevel fromString(String value) {
        if (value == null) {
            return LOW;
        }
        return switch (value.trim().toUpperCase()) {
            case "HIGH", "ВЫСОКИЙ", "CRITICAL" -> HIGH;
            case "MEDIUM", "СРЕДНИЙ", "MODERATE" -> MEDIUM;
            default -> LOW;
        };
    }
}
