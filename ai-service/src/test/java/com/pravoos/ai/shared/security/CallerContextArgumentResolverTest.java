package com.pravoos.ai.shared.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pravoos.common.web.OrgContext;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.ServletWebRequest;

class CallerContextArgumentResolverTest {

  private final CallerContextArgumentResolver resolver = new CallerContextArgumentResolver();
  private final UUID userId = UUID.randomUUID();
  private final UUID orgId = UUID.randomUUID();
  private final UUID clientId = UUID.randomUUID();

  @AfterEach
  void clearContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void supportsOnlyCallerContextParameters() throws Exception {
    assertThat(resolver.supportsParameter(parameterOf("callerHandler"))).isTrue();
    assertThat(resolver.supportsParameter(parameterOf("stringHandler"))).isFalse();
  }

  @Test
  void resolvesIdentityFromTheRequestPrincipal() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setUserPrincipal(authentication());

    CallerContext caller = resolve(request);

    assertThat(caller.userId()).isEqualTo(userId);
    assertThat(caller.orgIds()).containsExactly(orgId);
    assertThat(caller.clientIds()).containsExactly(clientId);
  }

  @Test
  void fallsBackToTheSecurityContextWhenTheRequestCarriesNoPrincipal() {
    SecurityContextHolder.getContext().setAuthentication(authentication());

    CallerContext caller = resolve(new MockHttpServletRequest());

    assertThat(caller.userId()).isEqualTo(userId);
    assertThat(caller.orgIds()).containsExactly(orgId);
  }

  @Test
  void readsTenantsWithoutRequiringAUserIdBearingPrincipal() {
    UsernamePasswordAuthenticationToken clientOnly =
        new UsernamePasswordAuthenticationToken("not-a-uuid", null);
    clientOnly.setDetails(new OrgContext(List.of(), List.of(clientId), null));
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setUserPrincipal(clientOnly);

    CallerContext caller = resolve(request);

    assertThat(caller.clientIds()).containsExactly(clientId);
    assertThatThrownBy(caller::userId).isInstanceOf(IllegalStateException.class);
  }

  private CallerContext resolve(MockHttpServletRequest request) {
    return resolver.resolveArgument(null, null, new ServletWebRequest(request), null);
  }

  private Authentication authentication() {
    UsernamePasswordAuthenticationToken authentication =
        new UsernamePasswordAuthenticationToken(userId.toString(), null);
    authentication.setDetails(new OrgContext(List.of(orgId), List.of(clientId), null));
    return authentication;
  }

  private MethodParameter parameterOf(String methodName) throws Exception {
    return new MethodParameter(
        Handlers.class.getDeclaredMethod(
            methodName, methodName.startsWith("caller") ? CallerContext.class : String.class),
        0);
  }

  private static final class Handlers {
    void callerHandler(CallerContext caller) {}

    void stringHandler(String value) {}
  }
}
