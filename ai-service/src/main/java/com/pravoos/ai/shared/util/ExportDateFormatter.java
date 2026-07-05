package com.pravoos.ai.shared.util;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class ExportDateFormatter {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private ExportDateFormatter() {
    }

    public static String format(LocalDateTime dateTime) {
        return dateTime == null ? "—" : FORMATTER.format(dateTime);
    }

    public static String formatDate(LocalDate date) {
        return date == null ? null : DATE_FORMATTER.format(date);
    }
}
