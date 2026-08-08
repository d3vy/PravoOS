package com.pravoos.ai.practice.internal.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pravoos.ai.practice.internal.dto.CreateInvoiceRequest;
import com.pravoos.ai.practice.internal.dto.ExportedFile;
import com.pravoos.ai.practice.internal.dto.InvoiceResponse;
import com.pravoos.ai.practice.internal.dto.InvoiceSummary;
import com.pravoos.ai.practice.internal.dto.UpdateInvoiceStatusRequest;
import com.pravoos.ai.practice.internal.service.InvoiceService;
import com.pravoos.ai.shared.exception.GlobalExceptionHandler;
import com.pravoos.ai.shared.exception.InvoiceNotFoundException;
import com.pravoos.ai.shared.model.enums.InvoiceStatus;
import com.pravoos.ai.shared.util.PagedResponse;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class InvoiceControllerTest {

  @Mock private InvoiceService invoiceService;
  @Mock private Authentication authentication;

  private MockMvc mockMvc;
  private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
  private final UUID lawyerId = UUID.randomUUID();
  private final UUID invoiceId = UUID.randomUUID();
  private final UUID clientId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    InvoiceController controller = new InvoiceController(invoiceService);
    mockMvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  private InvoiceResponse sampleInvoice() {
    return new InvoiceResponse(
        invoiceId,
        clientId,
        "Иванов Иван",
        "INV-001",
        InvoiceStatus.DRAFT,
        InvoiceStatus.DRAFT.getDisplayName(),
        null,
        null,
        "RUB",
        BigDecimal.valueOf(1000),
        null,
        null,
        BigDecimal.valueOf(1000),
        null,
        List.of(),
        LocalDateTime.now(ZoneOffset.UTC));
  }

  @Test
  void listReturnsPagedInvoices() throws Exception {
    when(authentication.getPrincipal()).thenReturn(lawyerId.toString());
    when(invoiceService.list(lawyerId, null, 0, 20))
        .thenReturn(
            new PageImpl<>(
                List.of(
                    new InvoiceSummary(
                        invoiceId,
                        clientId,
                        "Иванов Иван",
                        "INV-001",
                        InvoiceStatus.DRAFT,
                        InvoiceStatus.DRAFT.getDisplayName(),
                        null,
                        null,
                        "RUB",
                        BigDecimal.valueOf(1000),
                        LocalDateTime.now(ZoneOffset.UTC)))));

    mockMvc
        .perform(get("/api/ai/invoices").principal(authentication))
        .andExpect(status().isOk())
        .andExpect(header().string(PagedResponse.TOTAL_COUNT_HEADER, "1"))
        .andExpect(jsonPath("$[0].number").value("INV-001"));
  }

  @Test
  void createReturnsCreatedInvoice() throws Exception {
    when(authentication.getPrincipal()).thenReturn(lawyerId.toString());
    when(invoiceService.create(any(CreateInvoiceRequest.class), eq(lawyerId)))
        .thenReturn(sampleInvoice());

    mockMvc
        .perform(
            post("/api/ai/invoices")
                .principal(authentication)
                .contentType("application/json")
                .content(
                    objectMapper.writeValueAsString(
                        new CreateInvoiceRequest(clientId, null, null, null, null, null))))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.number").value("INV-001"));
  }

  @Test
  void createRejectsMissingClientId() throws Exception {
    mockMvc
        .perform(
            post("/api/ai/invoices")
                .contentType("application/json")
                .content(
                    objectMapper.writeValueAsString(
                        new CreateInvoiceRequest(null, null, null, null, null, null))))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(invoiceService);
  }

  @Test
  void getReturnsInvoice() throws Exception {
    when(authentication.getPrincipal()).thenReturn(lawyerId.toString());
    when(invoiceService.get(invoiceId, lawyerId)).thenReturn(sampleInvoice());

    mockMvc
        .perform(get("/api/ai/invoices/{invoiceId}", invoiceId).principal(authentication))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(invoiceId.toString()));
  }

  @Test
  void getReturnsNotFoundForUnknownInvoice() throws Exception {
    when(authentication.getPrincipal()).thenReturn(lawyerId.toString());
    when(invoiceService.get(invoiceId, lawyerId))
        .thenThrow(new InvoiceNotFoundException(invoiceId));

    mockMvc
        .perform(get("/api/ai/invoices/{invoiceId}", invoiceId).principal(authentication))
        .andExpect(status().isNotFound());
  }

  @Test
  void updateStatusDelegatesToService() throws Exception {
    when(authentication.getPrincipal()).thenReturn(lawyerId.toString());
    when(invoiceService.updateStatus(invoiceId, lawyerId, InvoiceStatus.ISSUED))
        .thenReturn(sampleInvoice());

    mockMvc
        .perform(
            patch("/api/ai/invoices/{invoiceId}/status", invoiceId)
                .principal(authentication)
                .contentType("application/json")
                .content(
                    objectMapper.writeValueAsString(
                        new UpdateInvoiceStatusRequest(InvoiceStatus.ISSUED))))
        .andExpect(status().isOk());
  }

  @Test
  void updateStatusRejectsNullStatus() throws Exception {
    mockMvc
        .perform(
            patch("/api/ai/invoices/{invoiceId}/status", invoiceId)
                .contentType("application/json")
                .content("{}"))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(invoiceService);
  }

  @Test
  void deleteReturnsNoContent() throws Exception {
    when(authentication.getPrincipal()).thenReturn(lawyerId.toString());

    mockMvc
        .perform(delete("/api/ai/invoices/{invoiceId}", invoiceId).principal(authentication))
        .andExpect(status().isNoContent());
    verify(invoiceService).delete(invoiceId, lawyerId);
  }

  @Test
  void exportReturnsPdfAttachment() throws Exception {
    when(authentication.getPrincipal()).thenReturn(lawyerId.toString());
    when(invoiceService.exportPdf(invoiceId, lawyerId))
        .thenReturn(new ExportedFile(new byte[] {1, 2, 3}, "invoice.pdf", "application/pdf"));

    mockMvc
        .perform(get("/api/ai/invoices/{invoiceId}/export", invoiceId).principal(authentication))
        .andExpect(status().isOk())
        .andExpect(
            header()
                .string(
                    "Content-Disposition", org.hamcrest.Matchers.containsString("invoice.pdf")));
  }
}
