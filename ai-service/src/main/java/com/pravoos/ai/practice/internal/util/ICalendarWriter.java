package com.pravoos.ai.practice.internal.util;

import com.pravoos.ai.practice.internal.dto.CalendarEventResponse;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

public final class ICalendarWriter {

    private static final String CRLF = "\r\n";
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");

    private ICalendarWriter() {
    }

    public static String write(List<CalendarEventResponse> events) {
        String stamp = TIMESTAMP.format(LocalDateTime.now(ZoneOffset.UTC));
        StringBuilder builder = new StringBuilder();
        builder.append("BEGIN:VCALENDAR").append(CRLF)
                .append("VERSION:2.0").append(CRLF)
                .append("PRODID:-//PravoOS//Calendar//RU").append(CRLF)
                .append("CALSCALE:GREGORIAN").append(CRLF)
                .append("METHOD:PUBLISH").append(CRLF);
        for (CalendarEventResponse event : events) {
            appendEvent(builder, event, stamp);
        }
        builder.append("END:VCALENDAR").append(CRLF);
        return builder.toString();
    }

    private static void appendEvent(StringBuilder builder, CalendarEventResponse event, String stamp) {
        String start = DATE.format(event.date());
        String end = DATE.format(event.date().plusDays(1));
        builder.append("BEGIN:VEVENT").append(CRLF)
                .append("UID:").append(event.id()).append("@pravoos").append(CRLF)
                .append("DTSTAMP:").append(stamp).append(CRLF)
                .append("DTSTART;VALUE=DATE:").append(start).append(CRLF)
                .append("DTEND;VALUE=DATE:").append(end).append(CRLF)
                .append("SUMMARY:").append(escape(summary(event))).append(CRLF);
        if (event.detail() != null && !event.detail().isBlank()) {
            builder.append("DESCRIPTION:").append(escape(event.detail())).append(CRLF);
        }
        builder.append("END:VEVENT").append(CRLF);
    }

    private static String summary(CalendarEventResponse event) {
        return "[" + event.typeName() + "] " + event.caseTitle() + " — " + event.title();
    }

    private static String escape(String value) {
        return value
                .replace("\\", "\\\\")
                .replace(";", "\\;")
                .replace(",", "\\,")
                .replace("\r\n", "\\n")
                .replace("\n", "\\n")
                .replace("\r", "\\n");
    }
}
