package com.pravoos.user.registration.internal.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pravoos.user.identity.api.LawyerProfileResponse;
import com.pravoos.user.registration.internal.dto.ApplicationResponse;
import com.pravoos.user.registration.internal.dto.ClientStatsResponse;
import com.pravoos.user.registration.internal.model.enums.ApplicationStatus;
import com.pravoos.user.registration.internal.service.AdminService;
import com.pravoos.user.registration.internal.service.ApplicationService;
import com.pravoos.user.shared.exception.ApplicationNotFoundException;
import com.pravoos.user.shared.exception.GlobalExceptionHandler;
import com.pravoos.user.shared.exception.LawyerNotFoundException;
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
class AdminControllerTest {

  @Mock private ApplicationService applicationService;
  @Mock private AdminService adminService;
  @Mock private Authentication authentication;

  private MockMvc mockMvc;
  private final UUID adminId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    AdminController controller = new AdminController(applicationService, adminService);
    mockMvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  @Test
  void getAllApplicationsReturnsListWithTotalCountHeader() throws Exception {
    UUID id = UUID.randomUUID();
    when(applicationService.countAllApplications()).thenReturn(5L);
    when(applicationService.getAllApplications(0, 200))
        .thenReturn(
            List.of(
                new ApplicationResponse(
                    id,
                    "a@b.com",
                    "A B",
                    "Spec",
                    "+1",
                    ApplicationStatus.PENDING,
                    null,
                    null,
                    true)));

    mockMvc
        .perform(get("/api/admin/applications"))
        .andExpect(status().isOk())
        .andExpect(header().string("X-Total-Count", "5"))
        .andExpect(jsonPath("$[0].id").value(id.toString()));
  }

  @Test
  void getPendingApplicationsUsesRequestedPageAndSize() throws Exception {
    when(applicationService.countPendingApplications()).thenReturn(1L);
    when(applicationService.getPendingApplications(2, 10)).thenReturn(List.of());

    mockMvc
        .perform(get("/api/admin/applications/pending").param("page", "2").param("size", "10"))
        .andExpect(status().isOk())
        .andExpect(header().string("X-Total-Count", "1"));

    verify(applicationService).getPendingApplications(2, 10);
  }

  @Test
  void approveDelegatesToServiceWithCurrentAdminId() throws Exception {
    when(authentication.getPrincipal()).thenReturn(adminId.toString());
    UUID appId = UUID.randomUUID();
    when(applicationService.approveApplication(appId, adminId))
        .thenReturn(
            new ApplicationResponse(
                appId,
                "a@b.com",
                "A B",
                "Spec",
                "+1",
                ApplicationStatus.APPROVED,
                null,
                null,
                true));

    mockMvc
        .perform(post("/api/admin/applications/{id}/approve", appId).principal(authentication))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("APPROVED"));
  }

  @Test
  void approveReturns404WhenApplicationNotFound() throws Exception {
    when(authentication.getPrincipal()).thenReturn(adminId.toString());
    UUID appId = UUID.randomUUID();
    when(applicationService.approveApplication(appId, adminId))
        .thenThrow(new ApplicationNotFoundException(appId));

    mockMvc
        .perform(post("/api/admin/applications/{id}/approve", appId).principal(authentication))
        .andExpect(status().isNotFound());
  }

  @Test
  void approveForceDelegatesWithForceFlag() throws Exception {
    when(authentication.getPrincipal()).thenReturn(adminId.toString());
    UUID appId = UUID.randomUUID();
    when(applicationService.approveApplication(appId, adminId, true))
        .thenReturn(
            new ApplicationResponse(
                appId,
                "a@b.com",
                "A B",
                "Spec",
                "+1",
                ApplicationStatus.APPROVED,
                null,
                null,
                true));

    mockMvc
        .perform(
            post("/api/admin/applications/{id}/approve-force", appId).principal(authentication))
        .andExpect(status().isOk());

    verify(applicationService).approveApplication(appId, adminId, true);
  }

  @Test
  void rejectDelegatesToServiceWithCurrentAdminId() throws Exception {
    when(authentication.getPrincipal()).thenReturn(adminId.toString());
    UUID appId = UUID.randomUUID();
    when(applicationService.rejectApplication(appId, adminId))
        .thenReturn(
            new ApplicationResponse(
                appId,
                "a@b.com",
                "A B",
                "Spec",
                "+1",
                ApplicationStatus.REJECTED,
                null,
                null,
                true));

    mockMvc
        .perform(post("/api/admin/applications/{id}/reject", appId).principal(authentication))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("REJECTED"));
  }

  @Test
  void getActiveLawyersReturnsListWithTotalCountHeader() throws Exception {
    UUID lawyerId = UUID.randomUUID();
    when(adminService.countActiveLawyers()).thenReturn(3L);
    when(adminService.getActiveLawyers(0, 200))
        .thenReturn(
            List.of(new LawyerProfileResponse(lawyerId, "l@b.com", "L B", "Spec", "+1", false)));

    mockMvc
        .perform(get("/api/admin/users/lawyers"))
        .andExpect(status().isOk())
        .andExpect(header().string("X-Total-Count", "3"))
        .andExpect(jsonPath("$[0].userId").value(lawyerId.toString()));
  }

  @Test
  void deleteLawyerReturns204OnSuccess() throws Exception {
    when(authentication.getPrincipal()).thenReturn(adminId.toString());
    UUID lawyerId = UUID.randomUUID();

    mockMvc
        .perform(delete("/api/admin/users/lawyers/{id}", lawyerId).principal(authentication))
        .andExpect(status().isNoContent());

    verify(adminService).deleteLawyer(lawyerId, adminId);
  }

  @Test
  void deleteLawyerReturns404WhenNotFound() throws Exception {
    when(authentication.getPrincipal()).thenReturn(adminId.toString());
    UUID lawyerId = UUID.randomUUID();
    org.mockito.Mockito.doThrow(new LawyerNotFoundException())
        .when(adminService)
        .deleteLawyer(eq(lawyerId), eq(adminId));

    mockMvc
        .perform(delete("/api/admin/users/lawyers/{id}", lawyerId).principal(authentication))
        .andExpect(status().isNotFound());
  }

  @Test
  void getClientStatsReturnsStats() throws Exception {
    when(adminService.getClientStats()).thenReturn(new ClientStatsResponse(7, 42));

    mockMvc
        .perform(get("/api/admin/stats/clients"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.newThisWeek").value(7))
        .andExpect(jsonPath("$.totalActive").value(42));
  }
}
