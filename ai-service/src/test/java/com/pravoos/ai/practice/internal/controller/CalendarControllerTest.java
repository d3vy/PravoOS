package com.pravoos.ai.practice.internal.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pravoos.ai.practice.internal.dto.CalendarEventResponse;
import com.pravoos.ai.practice.internal.service.CalendarService;
import com.pravoos.ai.shared.exception.GlobalExceptionHandler;
import com.pravoos.ai.shared.exception.PravoosException;
import com.pravoos.ai.shared.model.enums.CalendarEventType;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class CalendarControllerTest {

  @Mock private CalendarService calendarService;
  @Mock private Authentication authentication;

  private MockMvc mockMvc;
  private final UUID lawyerId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    CalendarController controller = new CalendarController(calendarService);
    mockMvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  @Test
  void eventsReturnsEventsForCurrentLawyer() throws Exception {
    when(authentication.getPrincipal()).thenReturn(lawyerId.toString());
    UUID caseId = UUID.randomUUID();
    when(calendarService.findEvents(
            eq(lawyerId),
            any(),
            eq(LocalDate.of(2026, 1, 1)),
            eq(LocalDate.of(2026, 1, 31)),
            isNull(),
            isNull()))
        .thenReturn(
            List.of(
                new CalendarEventResponse(
                    "deadline:" + caseId + ":FILING_DEADLINE",
                    CalendarEventType.DEADLINE,
                    "Дедлайн",
                    caseId,
                    "Дело №1",
                    "Подача документов",
                    null,
                    LocalDate.of(2026, 1, 15))));

    mockMvc
        .perform(
            get("/api/ai/calendar")
                .param("from", "2026-01-01")
                .param("to", "2026-01-31")
                .principal(authentication))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].caseTitle").value("Дело №1"))
        .andExpect(jsonPath("$[0].type").value("DEADLINE"));
  }

  @Test
  void eventsPropagatesInvalidRangeAsBadRequest() throws Exception {
    when(authentication.getPrincipal()).thenReturn(lawyerId.toString());
    when(calendarService.findEvents(eq(lawyerId), any(), any(), any(), isNull(), isNull()))
        .thenThrow(
            new PravoosException("Дата окончания раньше даты начала", HttpStatus.BAD_REQUEST));

    mockMvc
        .perform(
            get("/api/ai/calendar")
                .param("from", "2026-01-31")
                .param("to", "2026-01-01")
                .principal(authentication))
        .andExpect(status().isBadRequest());
  }

  @Test
  void eventsWithoutRequiredFromParamReturns500DueToUnmappedException() throws Exception {
    mockMvc
        .perform(get("/api/ai/calendar").param("to", "2026-01-31").principal(authentication))
        .andExpect(status().isInternalServerError());
  }

  @Test
  void exportReturnsIcsAttachment() throws Exception {
    when(authentication.getPrincipal()).thenReturn(lawyerId.toString());
    when(calendarService.findEvents(
            eq(lawyerId),
            any(),
            eq(LocalDate.of(2026, 1, 1)),
            eq(LocalDate.of(2026, 1, 31)),
            isNull(),
            isNull()))
        .thenReturn(List.of());

    mockMvc
        .perform(
            get("/api/ai/calendar/export.ics")
                .param("from", "2026-01-01")
                .param("to", "2026-01-31")
                .principal(authentication))
        .andExpect(status().isOk())
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                .string(
                    "Content-Disposition", org.hamcrest.Matchers.containsString("calendar.ics")));
  }
}
