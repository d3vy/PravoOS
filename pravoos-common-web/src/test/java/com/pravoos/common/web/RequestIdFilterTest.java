package com.pravoos.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestIdFilterTest {

  private final RequestIdFilter filter = new RequestIdFilter();

  @Test
  void generatesRequestId_whenHeaderMissing_andEchoesItInResponse() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain chain = (req, res) -> {};

    filter.doFilter(request, response, chain);

    String requestId = response.getHeader("X-Request-Id");
    assertThat(requestId).isNotBlank();
  }

  @Test
  void reusesExistingRequestIdHeader() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("X-Request-Id", "existing-id");
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain chain = (req, res) -> {};

    filter.doFilter(request, response, chain);

    assertThat(response.getHeader("X-Request-Id")).isEqualTo("existing-id");
  }

  @Test
  void blankRequestIdHeader_generatesNewOne() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("X-Request-Id", "   ");
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain chain = (req, res) -> {};

    filter.doFilter(request, response, chain);

    assertThat(response.getHeader("X-Request-Id")).isNotBlank().isNotEqualTo("   ");
  }

  @Test
  void putsRequestIdInMdcDuringChain_andClearsItAfterward() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("X-Request-Id", "mdc-id");
    MockHttpServletResponse response = new MockHttpServletResponse();
    AtomicReference<String> mdcDuringChain = new AtomicReference<>();
    FilterChain chain = (req, res) -> mdcDuringChain.set(MDC.get("requestId"));

    filter.doFilter(request, response, chain);

    assertThat(mdcDuringChain.get()).isEqualTo("mdc-id");
    assertThat(MDC.get("requestId")).isNull();
  }

  @Test
  void clearsMdc_evenWhenChainThrows() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain chain =
        (req, res) -> {
          throw new RuntimeException("downstream failure");
        };

    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> filter.doFilter(request, response, chain))
        .isInstanceOf(RuntimeException.class);
    assertThat(MDC.get("requestId")).isNull();
  }
}
