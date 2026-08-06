package com.pravoos.user.registration.internal.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pravoos.user.registration.internal.dto.ApplicationResponse;
import com.pravoos.user.registration.internal.model.enums.ApplicationStatus;
import com.pravoos.user.registration.internal.service.ApplicationService;
import com.pravoos.user.shared.exception.ApplicationNotFoundException;
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
class InternalApplicationControllerTest {

  @Mock private ApplicationService applicationService;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    InternalApplicationController controller =
        new InternalApplicationController(applicationService);
    mockMvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  @Test
  void getReturnsApplicationById() throws Exception {
    UUID id = UUID.randomUUID();
    when(applicationService.getApplicationById(id))
        .thenReturn(
            new ApplicationResponse(
                id, "a@b.com", "A B", "Spec", "+1", ApplicationStatus.PENDING, null, null, true));

    mockMvc
        .perform(get("/internal/applications/{id}", id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(id.toString()));
  }

  @Test
  void getReturns404WhenApplicationNotFound() throws Exception {
    UUID id = UUID.randomUUID();
    when(applicationService.getApplicationById(id)).thenThrow(new ApplicationNotFoundException(id));

    mockMvc.perform(get("/internal/applications/{id}", id)).andExpect(status().isNotFound());
  }

  @Test
  void approveDelegatesWithNullAdminId() throws Exception {
    UUID id = UUID.randomUUID();

    mockMvc.perform(post("/internal/applications/{id}/approve", id)).andExpect(status().isOk());

    verify(applicationService).approveApplication(id, null);
  }

  @Test
  void approveForceDelegatesWithNullAdminIdAndForceFlag() throws Exception {
    UUID id = UUID.randomUUID();

    mockMvc
        .perform(post("/internal/applications/{id}/approve-force", id))
        .andExpect(status().isOk());

    verify(applicationService).approveApplication(id, null, true);
  }

  @Test
  void rejectDelegatesWithNullAdminId() throws Exception {
    UUID id = UUID.randomUUID();

    mockMvc.perform(post("/internal/applications/{id}/reject", id)).andExpect(status().isOk());

    verify(applicationService).rejectApplication(id, null);
  }
}
