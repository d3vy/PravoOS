package com.pravoos.ai.util;

import com.pravoos.ai.model.entity.Case;
import com.pravoos.ai.model.entity.Client;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

public final class TemplatePlaceholderResolver {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private TemplatePlaceholderResolver() {
    }

    public static String resolve(String content, Case caseEntity, Client client) {
        Map<String, String> placeholders = buildPlaceholders(caseEntity, client);
        String resolved = content;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            resolved = resolved.replace("{{" + entry.getKey() + "}}", entry.getValue());
        }
        return resolved;
    }

    private static Map<String, String> buildPlaceholders(Case caseEntity, Client client) {
        Map<String, String> placeholders = new LinkedHashMap<>();
        placeholders.put("case_title", nullToEmpty(caseEntity.getTitle()));
        placeholders.put("case_description", nullToEmpty(caseEntity.getDescription()));
        placeholders.put("filing_deadline", formatDate(caseEntity.getFilingDeadline()));
        placeholders.put("next_hearing_date", formatDate(caseEntity.getNextHearingDate()));
        placeholders.put("expires_at", formatDate(caseEntity.getExpiresAt()));
        placeholders.put("client_name", client == null ? "" : nullToEmpty(client.getName()));
        placeholders.put("client_phone", client == null ? "" : nullToEmpty(client.getPhone()));
        placeholders.put("client_email", client == null ? "" : nullToEmpty(client.getEmail()));
        placeholders.put("client_inn", client == null ? "" : nullToEmpty(client.getInn()));
        placeholders.put("today", formatDate(LocalDate.now(ZoneOffset.UTC)));
        return placeholders;
    }

    private static String formatDate(LocalDate date) {
        return date == null ? "" : date.format(DATE_FORMAT);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
