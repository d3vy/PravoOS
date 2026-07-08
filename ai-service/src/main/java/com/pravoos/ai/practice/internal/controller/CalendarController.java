package com.pravoos.ai.practice.internal.controller;

import com.pravoos.ai.practice.internal.dto.CalendarEventResponse;
import com.pravoos.ai.practice.internal.service.CalendarService;
import com.pravoos.ai.practice.internal.util.ICalendarWriter;
import com.pravoos.ai.shared.util.SecureFileHeaders;
import com.pravoos.common.web.SecurityUtils;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/ai/calendar")
public class CalendarController {

    private static final MediaType TEXT_CALENDAR = new MediaType("text", "calendar", StandardCharsets.UTF_8);

    private final CalendarService calendarService;

    public CalendarController(CalendarService calendarService) {
        this.calendarService = calendarService;
    }

    @GetMapping
    public ResponseEntity<List<CalendarEventResponse>> events(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) UUID caseId,
            @RequestParam(required = false) UUID clientId,
            Authentication authentication) {
        return ResponseEntity.ok(calendarService.findEvents(
                SecurityUtils.currentUserId(authentication),
                SecurityUtils.currentOrgIds(authentication),
                from, to, caseId, clientId));
    }

    @GetMapping("/export.ics")
    public ResponseEntity<byte[]> export(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) UUID caseId,
            @RequestParam(required = false) UUID clientId,
            Authentication authentication) {
        List<CalendarEventResponse> events = calendarService.findEvents(
                SecurityUtils.currentUserId(authentication),
                SecurityUtils.currentOrgIds(authentication),
                from, to, caseId, clientId);
        byte[] body = ICalendarWriter.write(events).getBytes(StandardCharsets.UTF_8);
        HttpHeaders headers = new HttpHeaders();
        SecureFileHeaders.apply(headers);
        headers.setContentType(TEXT_CALENDAR);
        headers.setContentDisposition(ContentDisposition.attachment().filename("calendar.ics").build());
        return ResponseEntity.ok().headers(headers).body(body);
    }
}
