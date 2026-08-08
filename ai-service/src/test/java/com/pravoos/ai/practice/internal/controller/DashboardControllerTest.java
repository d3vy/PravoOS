package com.pravoos.ai.practice.internal.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pravoos.ai.practice.internal.dto.DashboardResponse;
import com.pravoos.ai.practice.internal.service.DashboardService;
import com.pravoos.ai.shared.exception.GlobalExceptionHandler;
import java.math.BigDecimal;
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
class DashboardControllerTest {

  @Mock private DashboardService dashboardService;
  @Mock private Authentication authentication;

  private MockMvc mockMvc;
  private final UUID lawyerId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    DashboardController controller = new DashboardController(dashboardService);
    mockMvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    when(authentication.getPrincipal()).thenReturn(lawyerId.toString());
  }

  @Test
  void dashboardReturnsSnapshotForCurrentLawyer() throws Exception {
    when(dashboardService.getDashboard(lawyerId))
        .thenReturn(
            new DashboardResponse(
                List.of(),
                5,
                2,
                List.of(),
                List.of(),
                new DashboardResponse.MoneyOnTable(120, BigDecimal.valueOf(6000)),
                List.of(),
                new DashboardResponse.UnpaidInvoicesSummary(0, BigDecimal.ZERO, List.of())));

    mockMvc
        .perform(get("/api/ai/dashboard").principal(authentication))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.activeCases").value(5))
        .andExpect(jsonPath("$.openTasks").value(2))
        .andExpect(jsonPath("$.moneyOnTable.uninvoicedMinutes").value(120));
  }
}
