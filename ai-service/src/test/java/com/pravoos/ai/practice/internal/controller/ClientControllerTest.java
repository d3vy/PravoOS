package com.pravoos.ai.practice.internal.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pravoos.ai.practice.internal.dto.ClientDetailResponse;
import com.pravoos.ai.practice.internal.dto.ClientResponse;
import com.pravoos.ai.practice.internal.dto.ConsentResponse;
import com.pravoos.ai.practice.internal.dto.CreateClientRequest;
import com.pravoos.ai.practice.internal.dto.UpdateClientRequest;
import com.pravoos.ai.practice.internal.service.ClientService;
import com.pravoos.ai.practice.internal.service.ConflictCheckService;
import com.pravoos.ai.recyclebin.api.DeletionActor;
import com.pravoos.ai.shared.exception.GlobalExceptionHandler;
import com.pravoos.ai.shared.model.enums.ClientType;
import com.pravoos.ai.shared.service.AccessAuditService;
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
class ClientControllerTest {

  @Mock private ClientService clientService;
  @Mock private ConflictCheckService conflictCheckService;
  @Mock private AccessAuditService accessAuditService;
  @Mock private Authentication authentication;

  private MockMvc mockMvc;
  private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
  private final UUID lawyerId = UUID.randomUUID();
  private final UUID clientId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    ClientController controller =
        new ClientController(clientService, conflictCheckService, accessAuditService);
    mockMvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  private ClientResponse sampleClient() {
    return new ClientResponse(
        clientId,
        "Иванов Иван",
        ClientType.INDIVIDUAL,
        ClientType.INDIVIDUAL.getDisplayName(),
        null,
        null,
        null,
        null,
        LocalDateTime.now(ZoneOffset.UTC),
        0);
  }

  @Test
  void createReturnsCreatedClient() throws Exception {
    when(authentication.getPrincipal()).thenReturn(lawyerId.toString());
    when(clientService.create(any(CreateClientRequest.class), eq(lawyerId)))
        .thenReturn(sampleClient());

    mockMvc
        .perform(
            post("/api/ai/clients")
                .principal(authentication)
                .contentType("application/json")
                .content(
                    objectMapper.writeValueAsString(
                        new CreateClientRequest(
                            "Иванов Иван", ClientType.INDIVIDUAL, null, null, null, null, true))))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.name").value("Иванов Иван"));
  }

  @Test
  void createRejectsMissingConsent() throws Exception {
    mockMvc
        .perform(
            post("/api/ai/clients")
                .contentType("application/json")
                .content(
                    objectMapper.writeValueAsString(
                        new CreateClientRequest(
                            "Иванов Иван", ClientType.INDIVIDUAL, null, null, null, null, false))))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(clientService);
  }

  @Test
  void getReturnsClientDetailAndRecordsAudit() throws Exception {
    when(authentication.getPrincipal()).thenReturn(lawyerId.toString());
    when(clientService.get(clientId, lawyerId))
        .thenReturn(new ClientDetailResponse(sampleClient(), List.of()));

    mockMvc
        .perform(get("/api/ai/clients/{clientId}", clientId).principal(authentication))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.client.name").value("Иванов Иван"));
    verify(accessAuditService).record(eq(authentication), any(), eq(clientId), any());
  }

  @Test
  void updateReturnsUpdatedClient() throws Exception {
    when(authentication.getPrincipal()).thenReturn(lawyerId.toString());
    when(clientService.update(eq(clientId), any(UpdateClientRequest.class), eq(lawyerId)))
        .thenReturn(sampleClient());

    mockMvc
        .perform(
            put("/api/ai/clients/{clientId}", clientId)
                .principal(authentication)
                .contentType("application/json")
                .content(
                    objectMapper.writeValueAsString(
                        new UpdateClientRequest(
                            "Иванов Иван", ClientType.INDIVIDUAL, null, null, null, null))))
        .andExpect(status().isOk());
  }

  @Test
  void updateRejectsBlankName() throws Exception {
    mockMvc
        .perform(
            put("/api/ai/clients/{clientId}", clientId)
                .contentType("application/json")
                .content(
                    objectMapper.writeValueAsString(
                        new UpdateClientRequest(
                            "", ClientType.INDIVIDUAL, null, null, null, null))))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(clientService);
  }

  @Test
  void invitePortalReturnsAccepted() throws Exception {
    when(authentication.getPrincipal()).thenReturn(lawyerId.toString());

    mockMvc
        .perform(
            post("/api/ai/clients/{clientId}/portal/invite", clientId).principal(authentication))
        .andExpect(status().isAccepted());
    verify(clientService).invitePortal(clientId, lawyerId);
  }

  @Test
  void consentReturnsNoContentWhenAbsent() throws Exception {
    when(authentication.getPrincipal()).thenReturn(lawyerId.toString());
    when(clientService.currentConsent(clientId, lawyerId)).thenReturn(null);

    mockMvc
        .perform(get("/api/ai/clients/{clientId}/consent", clientId).principal(authentication))
        .andExpect(status().isNoContent());
  }

  @Test
  void consentReturnsOkWhenPresent() throws Exception {
    when(authentication.getPrincipal()).thenReturn(lawyerId.toString());
    when(clientService.currentConsent(clientId, lawyerId))
        .thenReturn(new ConsentResponse("1.0", LocalDateTime.now(ZoneOffset.UTC), null, true));

    mockMvc
        .perform(get("/api/ai/clients/{clientId}/consent", clientId).principal(authentication))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.active").value(true));
  }

  @Test
  void revokeConsentReturnsNoContentAndRecordsAudit() throws Exception {
    when(authentication.getPrincipal()).thenReturn(lawyerId.toString());

    mockMvc
        .perform(delete("/api/ai/clients/{clientId}/consent", clientId).principal(authentication))
        .andExpect(status().isNoContent());
    verify(clientService).revokeConsent(clientId, lawyerId);
    verify(accessAuditService).record(eq(authentication), any(), eq(clientId), any());
  }

  @Test
  void deleteReturnsNoContent() throws Exception {
    when(authentication.getPrincipal()).thenReturn(lawyerId.toString());

    mockMvc
        .perform(delete("/api/ai/clients/{clientId}", clientId).principal(authentication))
        .andExpect(status().isNoContent());
    verify(clientService).delete(eq(clientId), any(DeletionActor.class), eq(false));
  }

  @Test
  void deleteWithCascadeParamPassesFlagThrough() throws Exception {
    when(authentication.getPrincipal()).thenReturn(lawyerId.toString());

    mockMvc
        .perform(
            delete("/api/ai/clients/{clientId}", clientId)
                .param("cascade", "true")
                .principal(authentication))
        .andExpect(status().isNoContent());
    verify(clientService).delete(eq(clientId), any(DeletionActor.class), eq(true));
  }
}
