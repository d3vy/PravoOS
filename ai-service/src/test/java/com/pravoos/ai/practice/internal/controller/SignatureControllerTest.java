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
import com.pravoos.ai.practice.internal.dto.CreateSignatureRequestDto;
import com.pravoos.ai.practice.internal.dto.DeclineSignatureRequest;
import com.pravoos.ai.practice.internal.dto.SignDocumentRequest;
import com.pravoos.ai.practice.internal.dto.SignatureRequestResponse;
import com.pravoos.ai.practice.internal.model.entity.Case;
import com.pravoos.ai.practice.internal.service.CaseService;
import com.pravoos.ai.practice.internal.service.SignatureService;
import com.pravoos.ai.practice.internal.service.SignatureService.SignatureFileDownload;
import com.pravoos.ai.shared.exception.CaseNotFoundException;
import com.pravoos.ai.shared.exception.GlobalExceptionHandler;
import com.pravoos.ai.shared.model.enums.SignatureProviderType;
import com.pravoos.ai.shared.model.enums.SignatureSignerRole;
import com.pravoos.ai.shared.model.enums.SignatureStatus;
import com.pravoos.ai.shared.security.CallerContextArgumentResolver;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class SignatureControllerTest {

  @Mock private SignatureService signatureService;
  @Mock private CaseService caseService;
  @Mock private Authentication authentication;
  @Mock private Case caseEntity;

  private MockMvc mockMvc;
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final UUID lawyerId = UUID.randomUUID();
  private final UUID caseId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    SignatureController controller = new SignatureController(signatureService, caseService);
    mockMvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setCustomArgumentResolvers(new CallerContextArgumentResolver())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  private SignatureRequestResponse sampleResponse(UUID signatureId) {
    return new SignatureRequestResponse(
        signatureId,
        UUID.randomUUID(),
        caseId,
        SignatureProviderType.SIMPLE,
        SignatureSignerRole.LAWYER,
        lawyerId,
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
  void createReturns201WithNewSignatureRequest() throws Exception {
    when(authentication.getPrincipal()).thenReturn(lawyerId.toString());
    UUID signatureId = UUID.randomUUID();
    CreateSignatureRequestDto request =
        new CreateSignatureRequestDto(UUID.randomUUID(), null, null, null, null, null);
    when(signatureService.create(eq(caseId), eq(request), eq(lawyerId)))
        .thenReturn(sampleResponse(signatureId));

    mockMvc
        .perform(
            post("/api/ai/cases/{caseId}/signatures", caseId)
                .principal(authentication)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(signatureId.toString()));
  }

  @Test
  void createReturns400WhenDocumentIdMissing() throws Exception {
    CreateSignatureRequestDto request =
        new CreateSignatureRequestDto(null, null, null, null, null, null);

    mockMvc
        .perform(
            post("/api/ai/cases/{caseId}/signatures", caseId)
                .principal(authentication)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void listReturnsSignaturesForCase() throws Exception {
    when(authentication.getPrincipal()).thenReturn(lawyerId.toString());
    UUID signatureId = UUID.randomUUID();
    when(signatureService.findByCase(caseId, lawyerId, List.of()))
        .thenReturn(List.of(sampleResponse(signatureId)));

    mockMvc
        .perform(get("/api/ai/cases/{caseId}/signatures", caseId).principal(authentication))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(1)));
  }

  @Test
  void listReturns404WhenCaseNotFound() throws Exception {
    when(authentication.getPrincipal()).thenReturn(lawyerId.toString());
    when(signatureService.findByCase(caseId, lawyerId, List.of()))
        .thenThrow(new CaseNotFoundException(caseId));

    mockMvc
        .perform(get("/api/ai/cases/{caseId}/signatures", caseId).principal(authentication))
        .andExpect(status().isNotFound());
  }

  @Test
  void exportProtocolReturnsPdfBytes() throws Exception {
    when(authentication.getPrincipal()).thenReturn(lawyerId.toString());
    UUID signatureId = UUID.randomUUID();
    when(caseService.requireVisibleCase(caseId, lawyerId, List.of())).thenReturn(caseEntity);
    when(signatureService.exportProtocol(caseEntity, signatureId)).thenReturn("pdf".getBytes());

    mockMvc
        .perform(
            get("/api/ai/cases/{caseId}/signatures/{signatureId}/protocol", caseId, signatureId)
                .principal(authentication))
        .andExpect(status().isOk())
        .andExpect(header().string("Content-Type", "application/pdf"));
  }

  @Test
  void downloadSignatureFileReturnsBytes() throws Exception {
    when(authentication.getPrincipal()).thenReturn(lawyerId.toString());
    UUID signatureId = UUID.randomUUID();
    when(caseService.requireVisibleCase(caseId, lawyerId, List.of())).thenReturn(caseEntity);
    when(signatureService.downloadSignatureFile(caseEntity, signatureId))
        .thenReturn(new SignatureFileDownload("bytes".getBytes(), "sig.p7s"));

    mockMvc
        .perform(
            get(
                    "/api/ai/cases/{caseId}/signatures/{signatureId}/signature-file",
                    caseId,
                    signatureId)
                .principal(authentication))
        .andExpect(status().isOk())
        .andExpect(header().string("Content-Type", "application/octet-stream"));
  }

  @Test
  void downloadSignatureFileEncodesFileNameInsteadOfInterpolatingIt() throws Exception {
    when(authentication.getPrincipal()).thenReturn(lawyerId.toString());
    UUID signatureId = UUID.randomUUID();
    when(caseService.requireVisibleCase(caseId, lawyerId, List.of())).thenReturn(caseEntity);
    when(signatureService.downloadSignatureFile(caseEntity, signatureId))
        .thenReturn(new SignatureFileDownload("bytes".getBytes(), "договор \";x=y.p7s"));

    mockMvc
        .perform(
            get(
                    "/api/ai/cases/{caseId}/signatures/{signatureId}/signature-file",
                    caseId,
                    signatureId)
                .principal(authentication))
        .andExpect(status().isOk())
        .andExpect(
            header()
                .string(
                    "Content-Disposition",
                    "attachment; "
                        + "filename=\"=?UTF-8?Q?=D0=B4=D0=BE=D0=B3=D0=BE=D0=B2=D0=BE=D1=80_=22;x=3Dy.p7s?=\"; "
                        + "filename*=UTF-8''%D0%B4%D0%BE%D0%B3%D0%BE%D0%B2%D0%BE%D1%80%20%22%3Bx%3Dy.p7s"));
  }

  @Test
  void signReturnsUpdatedRequest() throws Exception {
    when(authentication.getPrincipal()).thenReturn(lawyerId.toString());
    UUID signatureId = UUID.randomUUID();
    when(caseService.requireVisibleCase(caseId, lawyerId, List.of())).thenReturn(caseEntity);
    SignDocumentRequest request = new SignDocumentRequest("Ivan Ivanov", true);
    when(signatureService.signAsLawyer(eq(caseEntity), eq(signatureId), eq(request), any()))
        .thenReturn(sampleResponse(signatureId));

    mockMvc
        .perform(
            post("/api/ai/cases/{caseId}/signatures/{signatureId}/sign", caseId, signatureId)
                .principal(authentication)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(signatureId.toString()));
  }

  @Test
  void signWithCmsReturnsUpdatedRequest() throws Exception {
    when(authentication.getPrincipal()).thenReturn(lawyerId.toString());
    UUID signatureId = UUID.randomUUID();
    when(caseService.requireVisibleCase(caseId, lawyerId, List.of())).thenReturn(caseEntity);
    MockMultipartFile file =
        new MockMultipartFile("file", "sig.cms", "application/octet-stream", "cms".getBytes());
    when(signatureService.signWithCmsAsLawyer(
            eq(caseEntity), eq(signatureId), any(), eq("sig.cms"), any()))
        .thenReturn(sampleResponse(signatureId));

    mockMvc
        .perform(
            multipart(
                    "/api/ai/cases/{caseId}/signatures/{signatureId}/sign-cms", caseId, signatureId)
                .file(file)
                .principal(authentication))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(signatureId.toString()));
  }

  @Test
  void declineReturnsUpdatedRequest() throws Exception {
    when(authentication.getPrincipal()).thenReturn(lawyerId.toString());
    UUID signatureId = UUID.randomUUID();
    when(caseService.requireVisibleCase(caseId, lawyerId, List.of())).thenReturn(caseEntity);
    DeclineSignatureRequest request = new DeclineSignatureRequest("Отказ");
    when(signatureService.declineAsLawyer(eq(caseEntity), eq(signatureId), eq("Отказ"), any()))
        .thenReturn(sampleResponse(signatureId));

    mockMvc
        .perform(
            post("/api/ai/cases/{caseId}/signatures/{signatureId}/decline", caseId, signatureId)
                .principal(authentication)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(signatureId.toString()));
  }

  @Test
  void cancelReturnsUpdatedRequest() throws Exception {
    when(authentication.getPrincipal()).thenReturn(lawyerId.toString());
    UUID signatureId = UUID.randomUUID();
    when(signatureService.cancel(caseId, signatureId, lawyerId))
        .thenReturn(sampleResponse(signatureId));

    mockMvc
        .perform(
            post("/api/ai/cases/{caseId}/signatures/{signatureId}/cancel", caseId, signatureId)
                .principal(authentication))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(signatureId.toString()));
  }
}
