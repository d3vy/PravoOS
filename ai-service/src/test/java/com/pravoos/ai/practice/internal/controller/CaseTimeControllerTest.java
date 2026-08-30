package com.pravoos.ai.practice.internal.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pravoos.ai.practice.internal.dto.CaseTimeSummary;
import com.pravoos.ai.practice.internal.dto.CreateTimeEntryRequest;
import com.pravoos.ai.practice.internal.dto.StartTimerRequest;
import com.pravoos.ai.practice.internal.dto.TimeEntryResponse;
import com.pravoos.ai.practice.internal.dto.UpdateTimeEntryRequest;
import com.pravoos.ai.practice.internal.service.TimeEntryService;
import com.pravoos.ai.shared.exception.CaseNotFoundException;
import com.pravoos.ai.shared.exception.GlobalExceptionHandler;
import com.pravoos.ai.shared.security.CallerContextArgumentResolver;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class CaseTimeControllerTest {

  @Mock private TimeEntryService timeEntryService;
  @Mock private Authentication authentication;

  private MockMvc mockMvc;
  private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
  private final UUID lawyerId = UUID.randomUUID();
  private final UUID caseId = UUID.randomUUID();
  private final UUID entryId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    CaseTimeController controller = new CaseTimeController(timeEntryService);
    mockMvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setCustomArgumentResolvers(new CallerContextArgumentResolver())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  private TimeEntryResponse sampleEntry() {
    return new TimeEntryResponse(
        entryId,
        caseId,
        "Работа с документами",
        LocalDate.of(2026, 1, 10),
        60,
        BigDecimal.valueOf(3000),
        BigDecimal.valueOf(3000),
        true,
        false,
        null,
        false,
        LocalDateTime.now(ZoneOffset.UTC));
  }

  @Test
  void summaryReturnsAggregatedTime() throws Exception {
    when(authentication.getPrincipal()).thenReturn(lawyerId.toString());
    when(timeEntryService.summary(eq(caseId), eq(lawyerId), anyList()))
        .thenReturn(
            new CaseTimeSummary(
                List.of(sampleEntry()),
                60,
                60,
                60,
                BigDecimal.valueOf(3000),
                BigDecimal.valueOf(3000)));

    mockMvc
        .perform(get("/api/ai/cases/{caseId}/time", caseId).principal(authentication))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalMinutes").value(60));
  }

  @Test
  void summaryReturnsNotFoundForUnknownCase() throws Exception {
    when(authentication.getPrincipal()).thenReturn(lawyerId.toString());
    when(timeEntryService.summary(eq(caseId), eq(lawyerId), anyList()))
        .thenThrow(new CaseNotFoundException(caseId));

    mockMvc
        .perform(get("/api/ai/cases/{caseId}/time", caseId).principal(authentication))
        .andExpect(status().isNotFound());
  }

  @Test
  void createReturnsCreatedEntry() throws Exception {
    when(authentication.getPrincipal()).thenReturn(lawyerId.toString());
    when(timeEntryService.create(
            eq(caseId), any(CreateTimeEntryRequest.class), eq(lawyerId), anyList()))
        .thenReturn(sampleEntry());

    mockMvc
        .perform(
            post("/api/ai/cases/{caseId}/time", caseId)
                .principal(authentication)
                .contentType("application/json")
                .content(
                    objectMapper.writeValueAsString(
                        new CreateTimeEntryRequest(
                            "Работа с документами",
                            LocalDate.of(2026, 1, 10),
                            60,
                            BigDecimal.valueOf(3000),
                            true))))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(entryId.toString()));
  }

  @Test
  void createRejectsInvalidMinutes() throws Exception {
    mockMvc
        .perform(
            post("/api/ai/cases/{caseId}/time", caseId)
                .contentType("application/json")
                .content(
                    objectMapper.writeValueAsString(
                        new CreateTimeEntryRequest(
                            "Работа с документами",
                            LocalDate.of(2026, 1, 10),
                            0,
                            BigDecimal.valueOf(3000),
                            true))))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(timeEntryService);
  }

  @Test
  void updateReturnsUpdatedEntry() throws Exception {
    when(authentication.getPrincipal()).thenReturn(lawyerId.toString());
    when(timeEntryService.update(
            eq(caseId), eq(entryId), any(UpdateTimeEntryRequest.class), eq(lawyerId), anyList()))
        .thenReturn(sampleEntry());

    mockMvc
        .perform(
            patch("/api/ai/cases/{caseId}/time/{entryId}", caseId, entryId)
                .principal(authentication)
                .contentType("application/json")
                .content(
                    objectMapper.writeValueAsString(
                        new UpdateTimeEntryRequest(
                            "Работа с документами",
                            LocalDate.of(2026, 1, 10),
                            60,
                            BigDecimal.valueOf(3000),
                            true))))
        .andExpect(status().isOk());
  }

  @Test
  void deleteReturnsNoContent() throws Exception {
    when(authentication.getPrincipal()).thenReturn(lawyerId.toString());

    mockMvc
        .perform(
            delete("/api/ai/cases/{caseId}/time/{entryId}", caseId, entryId)
                .principal(authentication))
        .andExpect(status().isNoContent());
    verify(timeEntryService).delete(eq(caseId), eq(entryId), eq(lawyerId), anyList());
  }

  @Test
  void startTimerReturnsCreatedRunningEntry() throws Exception {
    when(authentication.getPrincipal()).thenReturn(lawyerId.toString());
    when(timeEntryService.startTimer(
            eq(caseId), any(StartTimerRequest.class), eq(lawyerId), anyList()))
        .thenReturn(
            new TimeEntryResponse(
                entryId,
                caseId,
                "Судебное заседание",
                LocalDate.of(2026, 1, 10),
                0,
                BigDecimal.valueOf(3000),
                BigDecimal.ZERO,
                true,
                true,
                Instant.now(),
                false,
                LocalDateTime.now(ZoneOffset.UTC)));

    mockMvc
        .perform(
            post("/api/ai/cases/{caseId}/time/timer/start", caseId)
                .principal(authentication)
                .contentType("application/json")
                .content(
                    objectMapper.writeValueAsString(
                        new StartTimerRequest(
                            "Судебное заседание", BigDecimal.valueOf(3000), true))))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.running").value(true));
  }

  @Test
  void startTimerRejectsBlankDescription() throws Exception {
    mockMvc
        .perform(
            post("/api/ai/cases/{caseId}/time/timer/start", caseId)
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(new StartTimerRequest("", null, true))))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(timeEntryService);
  }

  @Test
  void stopTimerReturnsStoppedEntry() throws Exception {
    when(authentication.getPrincipal()).thenReturn(lawyerId.toString());
    when(timeEntryService.stopTimer(eq(caseId), eq(lawyerId), anyList())).thenReturn(sampleEntry());

    mockMvc
        .perform(post("/api/ai/cases/{caseId}/time/timer/stop", caseId).principal(authentication))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.running").value(false));
  }
}
