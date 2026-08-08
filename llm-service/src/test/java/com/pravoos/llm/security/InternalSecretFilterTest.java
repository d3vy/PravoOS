package com.pravoos.llm.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class InternalSecretFilterTest {

  private InternalSecretVerifier verifier;
  private InternalSecretFilter filter;
  private FilterChain chain;

  @BeforeEach
  void setUp() {
    verifier = mock(InternalSecretVerifier.class);
    filter = new InternalSecretFilter(verifier);
    chain = mock(FilterChain.class);
  }

  @Test
  void skipsVerificationForNonInternalPaths() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");

    assertThat(filter.shouldNotFilter(request)).isTrue();
  }

  @Test
  void requiresVerificationForInternalPaths() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/internal/llm/complete");

    assertThat(filter.shouldNotFilter(request)).isFalse();
  }

  @Test
  void rejectsRequestWithMissingOrInvalidSecret() throws Exception {
    when(verifier.matches(null)).thenReturn(false);
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/internal/llm/complete");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilterInternal(request, response, chain);

    assertThat(response.getStatus()).isEqualTo(403);
    verify(chain, never()).doFilter(request, response);
  }

  @Test
  void allowsRequestWithValidSecret() throws Exception {
    when(verifier.matches("s3cr3t")).thenReturn(true);
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/internal/llm/complete");
    request.addHeader("X-Internal-Secret", "s3cr3t");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilterInternal(request, response, chain);

    verify(chain).doFilter(request, response);
    assertThat(response.getStatus()).isEqualTo(200);
  }
}
