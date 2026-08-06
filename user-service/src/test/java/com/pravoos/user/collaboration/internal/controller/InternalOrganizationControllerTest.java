package com.pravoos.user.collaboration.internal.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pravoos.user.collaboration.internal.service.OrganizationService;
import com.pravoos.user.shared.exception.GlobalExceptionHandler;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class InternalOrganizationControllerTest {

  @Mock private OrganizationService organizationService;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    InternalOrganizationController controller =
        new InternalOrganizationController(organizationService);
    mockMvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  @Test
  void checkMembership_returnsTrueWhenMember() throws Exception {
    UUID orgId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    when(organizationService.isMember(orgId, userId)).thenReturn(true);

    mockMvc
        .perform(get("/internal/org/{orgId}/members/{userId}", orgId, userId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.member").value(true));
  }

  @Test
  void checkMembership_returnsFalseWhenNotMember() throws Exception {
    UUID orgId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    when(organizationService.isMember(orgId, userId)).thenReturn(false);

    mockMvc
        .perform(get("/internal/org/{orgId}/members/{userId}", orgId, userId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.member").value(false));
  }
}
