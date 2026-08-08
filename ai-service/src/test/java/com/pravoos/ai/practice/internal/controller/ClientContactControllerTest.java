package com.pravoos.ai.practice.internal.controller;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.pravoos.ai.practice.internal.dto.ContactResponse;
import com.pravoos.ai.practice.internal.dto.CreateContactRequest;
import com.pravoos.ai.practice.internal.dto.UpdateContactRequest;
import com.pravoos.ai.practice.internal.service.ClientContactService;
import com.pravoos.ai.shared.exception.ClientContactNotFoundException;
import com.pravoos.ai.shared.exception.GlobalExceptionHandler;
import com.pravoos.ai.shared.model.enums.ContactType;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class ClientContactControllerTest {

  @Mock private ClientContactService contactService;
  @Mock private Authentication authentication;

  private MockMvc mockMvc;
  private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
  private final UUID lawyerId = UUID.randomUUID();
  private final UUID clientId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    ClientContactController controller = new ClientContactController(contactService);
    mockMvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  private ContactResponse sampleContact(UUID contactId) {
    return new ContactResponse(
        contactId,
        clientId,
        ContactType.CALL,
        "Звонок",
        LocalDate.now(),
        "notes",
        LocalDateTime.now(ZoneOffset.UTC));
  }

  @Test
  void listReturnsPageWithTotalCountHeader() throws Exception {
    when(authentication.getPrincipal()).thenReturn(lawyerId.toString());
    UUID contactId = UUID.randomUUID();
    when(contactService.findByClient(clientId, lawyerId, 0, 50))
        .thenReturn(new PageImpl<>(List.of(sampleContact(contactId)), PageRequest.of(0, 50), 1));

    mockMvc
        .perform(get("/api/ai/clients/{clientId}/contacts", clientId).principal(authentication))
        .andExpect(status().isOk())
        .andExpect(header().string("X-Total-Count", "1"))
        .andExpect(jsonPath("$[0].id").value(contactId.toString()));
  }

  @Test
  void createReturns201() throws Exception {
    when(authentication.getPrincipal()).thenReturn(lawyerId.toString());
    UUID contactId = UUID.randomUUID();
    CreateContactRequest request =
        new CreateContactRequest(ContactType.MEETING, LocalDate.now(), "notes");
    when(contactService.create(clientId, request, lawyerId)).thenReturn(sampleContact(contactId));

    mockMvc
        .perform(
            post("/api/ai/clients/{clientId}/contacts", clientId)
                .principal(authentication)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(contactId.toString()));
  }

  @Test
  void createReturns400WhenTypeMissing() throws Exception {
    String invalidJson = "{\"contactDate\":\"2026-01-01\"}";

    mockMvc
        .perform(
            post("/api/ai/clients/{clientId}/contacts", clientId)
                .principal(authentication)
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidJson))
        .andExpect(status().isBadRequest());
  }

  @Test
  void updateReturnsUpdatedContact() throws Exception {
    when(authentication.getPrincipal()).thenReturn(lawyerId.toString());
    UUID contactId = UUID.randomUUID();
    UpdateContactRequest request =
        new UpdateContactRequest(ContactType.LETTER, LocalDate.now(), "updated");
    when(contactService.update(clientId, contactId, request, lawyerId))
        .thenReturn(sampleContact(contactId));

    mockMvc
        .perform(
            put("/api/ai/clients/{clientId}/contacts/{contactId}", clientId, contactId)
                .principal(authentication)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk());
  }

  @Test
  void updateReturns404WhenContactBelongsToAnotherClient() throws Exception {
    when(authentication.getPrincipal()).thenReturn(lawyerId.toString());
    UUID contactId = UUID.randomUUID();
    UpdateContactRequest request =
        new UpdateContactRequest(ContactType.LETTER, LocalDate.now(), null);
    when(contactService.update(clientId, contactId, request, lawyerId))
        .thenThrow(new ClientContactNotFoundException(contactId));

    mockMvc
        .perform(
            put("/api/ai/clients/{clientId}/contacts/{contactId}", clientId, contactId)
                .principal(authentication)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isNotFound());
  }

  @Test
  void deleteReturns204() throws Exception {
    when(authentication.getPrincipal()).thenReturn(lawyerId.toString());
    UUID contactId = UUID.randomUUID();

    mockMvc
        .perform(
            delete("/api/ai/clients/{clientId}/contacts/{contactId}", clientId, contactId)
                .principal(authentication))
        .andExpect(status().isNoContent());
  }

  @Test
  void deleteReturns404WhenNotFound() throws Exception {
    when(authentication.getPrincipal()).thenReturn(lawyerId.toString());
    UUID contactId = UUID.randomUUID();
    doThrow(new ClientContactNotFoundException(contactId))
        .when(contactService)
        .delete(clientId, contactId, lawyerId);

    mockMvc
        .perform(
            delete("/api/ai/clients/{clientId}/contacts/{contactId}", clientId, contactId)
                .principal(authentication))
        .andExpect(status().isNotFound());
  }
}
