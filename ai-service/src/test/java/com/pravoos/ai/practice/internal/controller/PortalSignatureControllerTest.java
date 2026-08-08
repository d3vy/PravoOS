package com.pravoos.ai.practice.internal.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pravoos.ai.practice.internal.dto.DeclineSignatureRequest;
import com.pravoos.ai.practice.internal.dto.SignDocumentRequest;
import com.pravoos.ai.practice.internal.dto.SignatureRequestResponse;
import com.pravoos.ai.practice.internal.service.PortalSignatureService;
import com.pravoos.ai.shared.exception.GlobalExceptionHandler;
import com.pravoos.ai.shared.exception.PravoosException;
import com.pravoos.ai.shared.model.enums.SignatureProviderType;
import com.pravoos.ai.shared.model.enums.SignatureSignerRole;
import com.pravoos.ai.shared.model.enums.SignatureStatus;
import com.pravoos.common.web.OrgContext;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class PortalSignatureControllerTest {

  @Mock private PortalSignatureService portalSignatureService;
  @Mock private Authentication authentication;

  private MockMvc mockMvc;
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final UUID clientId = UUID.randomUUID();
  private final UUID userId = UUID.randomUUID();
  private final UUID caseId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    PortalSignatureController controller = new PortalSignatureController(portalSignatureService);
    mockMvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  private SignatureRequestResponse sampleResponse(UUID signatureId) {
    return new SignatureRequestResponse(
        signatureId,
        UUID.randomUUID(),
        caseId,
        SignatureProviderType.SIMPLE,
        SignatureSignerRole.CLIENT,
        null,
        SignatureStatus.PENDING,
        "hash",
        null,
        null,
        null,
        null,
        null,
        null,
        LocalDateTime.now(ZoneOffset.UTC),
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        false);
  }

  @Test
  void listPendingReturnsSignaturesForClient() throws Exception {
    when(authentication.getDetails())
        .thenReturn(new OrgContext(List.of(), List.of(clientId), null));
    UUID signatureId = UUID.randomUUID();
    when(portalSignatureService.listPending(List.of(clientId)))
        .thenReturn(List.of(sampleResponse(signatureId)));

    mockMvc
        .perform(get("/api/ai/portal/signatures").principal(authentication))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(1)))
        .andExpect(jsonPath("$[0].id").value(signatureId.toString()));
  }

  @Test
  void listByCaseReturnsSignaturesForCase() throws Exception {
    when(authentication.getDetails())
        .thenReturn(new OrgContext(List.of(), List.of(clientId), null));
    UUID signatureId = UUID.randomUUID();
    when(portalSignatureService.listByCase(caseId, List.of(clientId)))
        .thenReturn(List.of(sampleResponse(signatureId)));

    mockMvc
        .perform(get("/api/ai/portal/cases/{caseId}/signatures", caseId).principal(authentication))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(1)));
  }

  @Test
  void signReturnsUpdatedRequest() throws Exception {
    when(authentication.getPrincipal()).thenReturn(userId.toString());
    when(authentication.getDetails())
        .thenReturn(new OrgContext(List.of(), List.of(clientId), null));
    UUID signatureId = UUID.randomUUID();
    SignDocumentRequest request = new SignDocumentRequest("Ivan Ivanov", true);
    when(portalSignatureService.sign(eq(signatureId), eq(request), eq(List.of(clientId)), any()))
        .thenReturn(sampleResponse(signatureId));

    mockMvc
        .perform(
            post("/api/ai/portal/signatures/{signatureId}/sign", signatureId)
                .principal(authentication)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(signatureId.toString()));
  }

  @Test
  void signReturns400WhenConsentMissing() throws Exception {
    SignDocumentRequest request = new SignDocumentRequest("Ivan Ivanov", false);

    mockMvc
        .perform(
            post("/api/ai/portal/signatures/{signatureId}/sign", UUID.randomUUID())
                .principal(authentication)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void signReturns404WhenSignatureNotFound() throws Exception {
    when(authentication.getPrincipal()).thenReturn(userId.toString());
    when(authentication.getDetails())
        .thenReturn(new OrgContext(List.of(), List.of(clientId), null));
    UUID signatureId = UUID.randomUUID();
    SignDocumentRequest request = new SignDocumentRequest("Ivan Ivanov", true);
    when(portalSignatureService.sign(eq(signatureId), eq(request), eq(List.of(clientId)), any()))
        .thenThrow(
            new PravoosException(
                "Запрос на подпись не найден", HttpStatus.NOT_FOUND, "SIGNATURE_NOT_FOUND"));

    mockMvc
        .perform(
            post("/api/ai/portal/signatures/{signatureId}/sign", signatureId)
                .principal(authentication)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isNotFound());
  }

  @Test
  void signWithCmsReturnsUpdatedRequest() throws Exception {
    when(authentication.getPrincipal()).thenReturn(userId.toString());
    when(authentication.getDetails())
        .thenReturn(new OrgContext(List.of(), List.of(clientId), null));
    UUID signatureId = UUID.randomUUID();
    MockMultipartFile file =
        new MockMultipartFile("file", "sig.cms", "application/octet-stream", "cms".getBytes());
    when(portalSignatureService.signWithCms(
            eq(signatureId), any(), eq("sig.cms"), eq(List.of(clientId)), any()))
        .thenReturn(sampleResponse(signatureId));

    mockMvc
        .perform(
            multipart("/api/ai/portal/signatures/{signatureId}/sign-cms", signatureId)
                .file(file)
                .principal(authentication))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(signatureId.toString()));
  }

  @Test
  void exportProtocolReturnsPdfBytes() throws Exception {
    when(authentication.getDetails())
        .thenReturn(new OrgContext(List.of(), List.of(clientId), null));
    UUID signatureId = UUID.randomUUID();
    when(portalSignatureService.exportProtocol(signatureId, List.of(clientId)))
        .thenReturn("pdf-bytes".getBytes());

    mockMvc
        .perform(
            get("/api/ai/portal/signatures/{signatureId}/protocol", signatureId)
                .principal(authentication))
        .andExpect(status().isOk())
        .andExpect(header().string("Content-Type", "application/pdf"));
  }

  @Test
  void declineReturnsUpdatedRequest() throws Exception {
    when(authentication.getPrincipal()).thenReturn(userId.toString());
    when(authentication.getDetails())
        .thenReturn(new OrgContext(List.of(), List.of(clientId), null));
    UUID signatureId = UUID.randomUUID();
    DeclineSignatureRequest request = new DeclineSignatureRequest("Не согласен");
    when(portalSignatureService.decline(
            eq(signatureId), eq("Не согласен"), eq(List.of(clientId)), any()))
        .thenReturn(sampleResponse(signatureId));

    mockMvc
        .perform(
            post("/api/ai/portal/signatures/{signatureId}/decline", signatureId)
                .principal(authentication)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(signatureId.toString()));
  }

  @Test
  void declineReturns400WhenReasonTooLong() throws Exception {
    DeclineSignatureRequest request = new DeclineSignatureRequest("x".repeat(1001));

    mockMvc
        .perform(
            post("/api/ai/portal/signatures/{signatureId}/decline", UUID.randomUUID())
                .principal(authentication)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest());
  }
}
