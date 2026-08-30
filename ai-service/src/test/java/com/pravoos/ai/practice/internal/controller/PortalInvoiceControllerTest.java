package com.pravoos.ai.practice.internal.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pravoos.ai.practice.internal.dto.InvoicePaymentResponse;
import com.pravoos.ai.practice.internal.dto.InvoiceResponse;
import com.pravoos.ai.practice.internal.dto.InvoiceSummary;
import com.pravoos.ai.practice.internal.service.InvoicePaymentService;
import com.pravoos.ai.practice.internal.service.PortalInvoiceService;
import com.pravoos.ai.shared.exception.GlobalExceptionHandler;
import com.pravoos.ai.shared.exception.InvoiceNotFoundException;
import com.pravoos.ai.shared.model.enums.InvoiceStatus;
import com.pravoos.ai.shared.security.CallerContextArgumentResolver;
import com.pravoos.common.web.OrgContext;
import java.math.BigDecimal;
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
class PortalInvoiceControllerTest {

  @Mock private PortalInvoiceService portalInvoiceService;
  @Mock private InvoicePaymentService invoicePaymentService;
  @Mock private Authentication authentication;

  private MockMvc mockMvc;
  private final UUID clientId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    PortalInvoiceController controller =
        new PortalInvoiceController(portalInvoiceService, invoicePaymentService);
    mockMvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setCustomArgumentResolvers(new CallerContextArgumentResolver())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    when(authentication.getDetails())
        .thenReturn(new OrgContext(List.of(), List.of(clientId), null));
  }

  @Test
  void listReturnsInvoicesVisibleToClient() throws Exception {
    UUID invoiceId = UUID.randomUUID();
    when(portalInvoiceService.listInvoices(List.of(clientId)))
        .thenReturn(
            List.of(
                new InvoiceSummary(
                    invoiceId,
                    clientId,
                    "Client",
                    "INV-1",
                    InvoiceStatus.ISSUED,
                    "Выставлен",
                    null,
                    null,
                    "RUB",
                    BigDecimal.TEN,
                    LocalDateTime.now(ZoneOffset.UTC))));

    mockMvc
        .perform(get("/api/ai/portal/invoices").principal(authentication))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(1)))
        .andExpect(jsonPath("$[0].id").value(invoiceId.toString()));
  }

  @Test
  void getReturnsInvoiceDetail() throws Exception {
    UUID invoiceId = UUID.randomUUID();
    when(portalInvoiceService.getInvoice(invoiceId, List.of(clientId)))
        .thenReturn(
            new InvoiceResponse(
                invoiceId,
                clientId,
                "Client",
                "INV-1",
                InvoiceStatus.ISSUED,
                "Выставлен",
                null,
                null,
                "RUB",
                BigDecimal.TEN,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.TEN,
                null,
                List.of(),
                LocalDateTime.now(ZoneOffset.UTC)));

    mockMvc
        .perform(get("/api/ai/portal/invoices/{invoiceId}", invoiceId).principal(authentication))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.number").value("INV-1"));
  }

  @Test
  void getReturns404WhenInvoiceNotVisible() throws Exception {
    UUID invoiceId = UUID.randomUUID();
    when(portalInvoiceService.getInvoice(invoiceId, List.of(clientId)))
        .thenThrow(new InvoiceNotFoundException(invoiceId));

    mockMvc
        .perform(get("/api/ai/portal/invoices/{invoiceId}", invoiceId).principal(authentication))
        .andExpect(status().isNotFound());
  }

  @Test
  void payReturnsConfirmationUrl() throws Exception {
    UUID invoiceId = UUID.randomUUID();
    when(invoicePaymentService.createPayment(invoiceId, List.of(clientId)))
        .thenReturn(new InvoicePaymentResponse(invoiceId, "https://pay.example.com/confirm"));

    mockMvc
        .perform(
            post("/api/ai/portal/invoices/{invoiceId}/pay", invoiceId).principal(authentication))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.confirmationUrl").value("https://pay.example.com/confirm"));
  }

  @Test
  void payReturns404WhenInvoiceNotVisible() throws Exception {
    UUID invoiceId = UUID.randomUUID();
    when(invoicePaymentService.createPayment(invoiceId, List.of(clientId)))
        .thenThrow(new InvoiceNotFoundException(invoiceId));

    mockMvc
        .perform(
            post("/api/ai/portal/invoices/{invoiceId}/pay", invoiceId).principal(authentication))
        .andExpect(status().isNotFound());
  }
}
