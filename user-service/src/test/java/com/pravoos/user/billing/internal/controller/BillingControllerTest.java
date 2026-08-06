package com.pravoos.user.billing.internal.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pravoos.user.billing.internal.dto.BillingStatusResponse;
import com.pravoos.user.billing.internal.dto.CheckoutRequest;
import com.pravoos.user.billing.internal.dto.CheckoutResponse;
import com.pravoos.user.billing.internal.dto.PaymentResponse;
import com.pravoos.user.billing.internal.dto.PlanResponse;
import com.pravoos.user.billing.internal.model.enums.PaymentStatus;
import com.pravoos.user.billing.internal.model.enums.SubscriptionStatus;
import com.pravoos.user.billing.internal.service.PaymentService;
import com.pravoos.user.billing.internal.service.SubscriptionService;
import com.pravoos.user.shared.exception.GlobalExceptionHandler;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class BillingControllerTest {

  @Mock private SubscriptionService subscriptionService;
  @Mock private PaymentService paymentService;
  @Mock private Authentication authentication;

  private MockMvc mockMvc;
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final UUID userId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    BillingController controller = new BillingController(subscriptionService, paymentService);
    mockMvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  @Test
  void getBilling_returnsCurrentStatus() throws Exception {
    when(authentication.getPrincipal()).thenReturn(userId.toString());
    when(subscriptionService.getStatus(userId))
        .thenReturn(
            new BillingStatusResponse(
                "PRO",
                "Профи",
                SubscriptionStatus.ACTIVE,
                null,
                LocalDateTime.now(),
                false,
                100,
                50_000L,
                5));

    mockMvc
        .perform(get("/api/user/billing").principal(authentication))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.planCode").value("PRO"))
        .andExpect(jsonPath("$.status").value("ACTIVE"));
  }

  @Test
  void getPlans_returnsAvailablePlans() throws Exception {
    when(subscriptionService.listPlans())
        .thenReturn(List.of(new PlanResponse("SOLO", "Соло", 0, 50, 10_000L, 1, true)));

    mockMvc
        .perform(get("/api/user/billing/plans"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].code").value("SOLO"))
        .andExpect(jsonPath("$[0].isDefault").value(true));
  }

  @Test
  void subscribe_returns400ForBlankPlanCode() throws Exception {
    mockMvc
        .perform(
            post("/api/user/billing/subscribe")
                .principal(authentication)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CheckoutRequest(""))))
        .andExpect(status().isBadRequest());
  }

  @Test
  void subscribe_delegatesToPaymentServiceAndReturnsCheckoutResponse() throws Exception {
    when(authentication.getPrincipal()).thenReturn(userId.toString());
    UUID paymentId = UUID.randomUUID();
    when(paymentService.startCheckout(eq(userId), eq("PRO")))
        .thenReturn(new CheckoutResponse(paymentId, "https://yookassa.ru/confirm/1"));

    mockMvc
        .perform(
            post("/api/user/billing/subscribe")
                .principal(authentication)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CheckoutRequest("PRO"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.paymentId").value(paymentId.toString()))
        .andExpect(jsonPath("$.confirmationUrl").value("https://yookassa.ru/confirm/1"));
  }

  @Test
  void cancel_delegatesToSubscriptionServiceAndReturnsUpdatedStatus() throws Exception {
    when(authentication.getPrincipal()).thenReturn(userId.toString());
    when(subscriptionService.cancel(userId))
        .thenReturn(
            new BillingStatusResponse(
                "PRO",
                "Профи",
                SubscriptionStatus.ACTIVE,
                null,
                LocalDateTime.now(),
                true,
                100,
                50_000L,
                5));

    mockMvc
        .perform(post("/api/user/billing/cancel").principal(authentication))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.cancelAtPeriodEnd").value(true));
  }

  @Test
  void getPayments_returnsHistory() throws Exception {
    when(authentication.getPrincipal()).thenReturn(userId.toString());
    UUID paymentId = UUID.randomUUID();
    when(paymentService.history(userId))
        .thenReturn(
            List.of(
                new PaymentResponse(
                    paymentId,
                    "PRO",
                    99000,
                    PaymentStatus.SUCCEEDED,
                    null,
                    LocalDateTime.now(),
                    LocalDateTime.now())));

    mockMvc
        .perform(get("/api/user/billing/payments").principal(authentication))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(paymentId.toString()))
        .andExpect(jsonPath("$[0].status").value("SUCCEEDED"));
  }
}
