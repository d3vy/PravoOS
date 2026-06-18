package com.pravoos.ai.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class ExportDateFormatter {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    private ExportDateFormatter() {
    }

    public static String format(LocalDateTime dateTime) {
        return dateTime == null ? "—" : FORMATTER.format(dateTime);
    }
}
