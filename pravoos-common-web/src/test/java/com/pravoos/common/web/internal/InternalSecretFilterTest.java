package com.pravoos.common.web.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.pravoos.common.security.internal.InternalCallerHeaders;
import com.pravoos.common.security.internal.InternalCallerProperties;
import com.pravoos.common.security.internal.InternalCallerProperties.Caller;
import com.pravoos.common.security.internal.InternalCallerVerifier;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class InternalSecretFilterTest {

  private static final String SECRET = "ai-secret-value";

  private final InternalSecretFilter filter =
      new InternalSecretFilter(
          new InternalCallerVerifier(
              new InternalCallerProperties(
                  Map.of("ai-service", new Caller(SECRET, List.of("/internal/portal-invites"))))));

  @Test
  void passesThroughValidInternalCall() throws Exception {
    MockFilterChain chain = new MockFilterChain();
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request("/internal/portal-invites", "ai-service", SECRET), response, chain);

    assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
    assertThat(chain.getRequest()).isNotNull();
  }

  @Test
  void rejectsCallWithoutCallerHeader() throws Exception {
    MockFilterChain chain = new MockFilterChain();
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request("/internal/portal-invites", null, SECRET), response, chain);

    assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
    assertThat(chain.getRequest()).isNull();
  }

  @Test
  void rejectsCallOutsideTheCallersAllowedPaths() throws Exception {
    MockFilterChain chain = new MockFilterChain();
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(
        request("/internal/applications/1/approve-force", "ai-service", SECRET), response, chain);

    assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
    assertThat(chain.getRequest()).isNull();
  }

  @Test
  void leavesPublicEndpointsUntouched() throws Exception {
    MockFilterChain chain = new MockFilterChain();
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request("/api/user/profile", null, null), response, chain);

    assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
    assertThat(chain.getRequest()).isNotNull();
  }

  private MockHttpServletRequest request(String uri, String caller, String secret) {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
    request.setRequestURI(uri);
    if (caller != null) {
      request.addHeader(InternalCallerHeaders.CALLER, caller);
    }
    if (secret != null) {
      request.addHeader(InternalCallerHeaders.SECRET, secret);
    }
    return request;
  }
}
