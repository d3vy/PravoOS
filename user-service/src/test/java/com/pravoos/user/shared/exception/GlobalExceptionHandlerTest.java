package com.pravoos.user.shared.exception;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pravoos.common.exception.InvalidPhoneNumberException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.ResourceAccessException;

class GlobalExceptionHandlerTest {

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(new ThrowingController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  @Test
  void accountLockedMapsTo429WithRetryAfterHeader() throws Exception {
    mockMvc
        .perform(get("/throw/account-locked"))
        .andExpect(status().isTooManyRequests())
        .andExpect(header().string("Retry-After", "42"))
        .andExpect(jsonPath("$.message").value("Too many failed login attempts. Try again later."));
  }

  @Test
  void pravoosExceptionMapsToItsOwnStatusAndCode() throws Exception {
    mockMvc
        .perform(get("/throw/pravoos"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message").value("conflict"))
        .andExpect(jsonPath("$.code").value("SOME_CODE"));
  }

  @Test
  void invalidPhoneNumberMapsTo400() throws Exception {
    mockMvc
        .perform(get("/throw/invalid-phone"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Некорректный телефон. Укажите номер из 11 цифр."));
  }

  @Test
  void validationErrorsAreJoinedAndMapTo400() throws Exception {
    mockMvc
        .perform(get("/throw/validate").contentType("application/json").content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("must not be blank"));
  }

  @Test
  void malformedBodyMapsTo400() throws Exception {
    mockMvc
        .perform(get("/throw/validate").contentType("application/json").content("not-json"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Malformed request body"));
  }

  @Test
  void typeMismatchMapsTo400WithParameterName() throws Exception {
    mockMvc
        .perform(get("/throw/type-mismatch").param("id", "not-a-number"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Invalid value for parameter 'id'"));
  }

  @Test
  void dataIntegrityViolationMapsTo409WithConstraintCode() throws Exception {
    mockMvc
        .perform(get("/throw/data-integrity"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message").value("Операция конфликтует с существующими данными"))
        .andExpect(jsonPath("$.code").value("CONSTRAINT_VIOLATION"));
  }

  @Test
  void upstreamUnavailableMapsTo502WithErrorId() throws Exception {
    mockMvc
        .perform(get("/throw/upstream"))
        .andExpect(status().isBadGateway())
        .andExpect(
            jsonPath("$.message").value("Внешний сервис временно недоступен, попробуйте позже"))
        .andExpect(jsonPath("$.code").value("UPSTREAM_UNAVAILABLE"))
        .andExpect(jsonPath("$.errorId").exists());
  }

  @Test
  void methodNotSupportedMapsTo405() throws Exception {
    mockMvc
        .perform(post("/throw/pravoos"))
        .andExpect(status().isMethodNotAllowed())
        .andExpect(jsonPath("$.message").value("Method not allowed"));
  }

  @Test
  void mediaTypeNotSupportedMapsTo415() throws Exception {
    mockMvc
        .perform(get("/throw/validate").contentType("text/plain").content("{}"))
        .andExpect(status().isUnsupportedMediaType())
        .andExpect(jsonPath("$.message").value("Unsupported media type"));
  }

  @Test
  void unmappedExceptionFallsBackTo500WithErrorId() throws Exception {
    mockMvc
        .perform(get("/throw/unexpected"))
        .andExpect(status().isInternalServerError())
        .andExpect(
            jsonPath("$.message")
                .value("Внутренняя ошибка сервера, обратитесь в поддержку с кодом ошибки"))
        .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
        .andExpect(jsonPath("$.errorId").exists());
  }

  @RestController
  static class ThrowingController {

    @RequestMapping("/throw/account-locked")
    void accountLocked() {
      throw new AccountLockedException(42L);
    }

    @RequestMapping(
        value = "/throw/pravoos",
        method = org.springframework.web.bind.annotation.RequestMethod.GET)
    void pravoos() {
      throw new PravoosException("conflict", HttpStatus.CONFLICT, "SOME_CODE");
    }

    @RequestMapping("/throw/invalid-phone")
    void invalidPhone() {
      throw new InvalidPhoneNumberException();
    }

    @RequestMapping(
        value = "/throw/validate",
        consumes = org.springframework.http.MediaType.APPLICATION_JSON_VALUE)
    void validate(@Valid @RequestBody ValidatedBody body) {}

    @RequestMapping("/throw/type-mismatch")
    void typeMismatch(@RequestParam("id") Long id) {}

    @RequestMapping("/throw/data-integrity")
    void dataIntegrity() {
      throw new DataIntegrityViolationException("duplicate key");
    }

    @RequestMapping("/throw/upstream")
    void upstream() {
      throw new ResourceAccessException("timeout");
    }

    @RequestMapping("/throw/unexpected")
    void unexpected() {
      throw new IllegalStateException("boom");
    }
  }

  static class ValidatedBody {
    @NotBlank private String name;

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }
  }
}
