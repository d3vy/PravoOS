package com.pravoos.ai.practice.internal.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pravoos.ai.practice.internal.dto.TimeEntryResponse;
import com.pravoos.ai.practice.internal.service.TimeEntryService;
import com.pravoos.ai.shared.exception.GlobalExceptionHandler;
import com.pravoos.ai.shared.security.CallerContextArgumentResolver;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
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
class TimeTrackerControllerTest {

  @Mock private TimeEntryService timeEntryService;
  @Mock private Authentication authentication;

  private MockMvc mockMvc;
  private final UUID lawyerId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    TimeTrackerController controller = new TimeTrackerController(timeEntryService);
    mockMvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setCustomArgumentResolvers(new CallerContextArgumentResolver())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    when(authentication.getPrincipal()).thenReturn(lawyerId.toString());
  }

  @Test
  void activeTimerReturnsRunningEntry() throws Exception {
    UUID entryId = UUID.randomUUID();
    when(timeEntryService.activeTimer(lawyerId))
        .thenReturn(
            Optional.of(
                new TimeEntryResponse(
                    entryId,
                    UUID.randomUUID(),
                    "work",
                    LocalDate.now(),
                    0,
                    BigDecimal.TEN,
                    BigDecimal.ZERO,
                    true,
                    true,
                    java.time.Instant.now(),
                    false,
                    LocalDateTime.now(ZoneOffset.UTC))));

    mockMvc
        .perform(get("/api/ai/time/active").principal(authentication))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(entryId.toString()))
        .andExpect(jsonPath("$.running").value(true));
  }

  @Test
  void activeTimerReturns204WhenNoneRunning() throws Exception {
    when(timeEntryService.activeTimer(lawyerId)).thenReturn(Optional.empty());

    mockMvc
        .perform(get("/api/ai/time/active").principal(authentication))
        .andExpect(status().isNoContent());
  }
}
