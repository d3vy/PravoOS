package com.pravoos.common.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;

class SecurityUtilsTest {

  @Test
  void currentUserId_returnsParsedUuidFromStringPrincipal() {
    UUID userId = UUID.randomUUID();
    Authentication authentication = mock(Authentication.class);
    when(authentication.getPrincipal()).thenReturn(userId.toString());

    assertThat(SecurityUtils.currentUserId(authentication)).isEqualTo(userId);
  }

  @Test
  void currentUserId_throwsWhenAuthenticationIsNull() {
    assertThatIllegalStateException().isThrownBy(() -> SecurityUtils.currentUserId(null));
  }

  @Test
  void currentUserId_throwsWhenPrincipalIsNotAString() {
    Authentication authentication = mock(Authentication.class);
    when(authentication.getPrincipal()).thenReturn(new Object());

    assertThatIllegalStateException().isThrownBy(() -> SecurityUtils.currentUserId(authentication));
  }

  @Test
  void currentUserId_throwsWhenPrincipalIsNotAValidUuid() {
    Authentication authentication = mock(Authentication.class);
    when(authentication.getPrincipal()).thenReturn("not-a-uuid");

    assertThatIllegalStateException().isThrownBy(() -> SecurityUtils.currentUserId(authentication));
  }

  @Test
  void currentOrgIds_returnsEmptyList_whenAuthenticationNullOrDetailsNotOrgContext() {
    assertThat(SecurityUtils.currentOrgIds(null)).isEmpty();

    Authentication authentication = mock(Authentication.class);
    when(authentication.getDetails()).thenReturn("not-org-context");
    assertThat(SecurityUtils.currentOrgIds(authentication)).isEmpty();
  }

  @Test
  void currentOrgIds_returnsOrgIdsFromOrgContextDetails() {
    UUID orgId = UUID.randomUUID();
    OrgContext orgContext = new OrgContext(List.of(orgId));
    Authentication authentication = mock(Authentication.class);
    when(authentication.getDetails()).thenReturn(orgContext);

    assertThat(SecurityUtils.currentOrgIds(authentication)).containsExactly(orgId);
  }

  @Test
  void currentClientIds_returnsClientIdsFromOrgContextDetails() {
    UUID clientId = UUID.randomUUID();
    OrgContext orgContext = new OrgContext(List.of(), List.of(clientId));
    Authentication authentication = mock(Authentication.class);
    when(authentication.getDetails()).thenReturn(orgContext);

    assertThat(SecurityUtils.currentClientIds(authentication)).containsExactly(clientId);
  }

  @Test
  void currentClientIds_returnsEmptyList_whenDetailsNotOrgContext() {
    assertThat(SecurityUtils.currentClientIds(null)).isEmpty();
  }

  @Test
  void currentAiProcessingMode_returnsModeFromOrgContext() {
    OrgContext orgContext =
        new OrgContext(List.of(), List.of(), null, AiProcessingMode.CROSS_BORDER);
    Authentication authentication = mock(Authentication.class);
    when(authentication.getDetails()).thenReturn(orgContext);

    assertThat(SecurityUtils.currentAiProcessingMode(authentication))
        .isEqualTo(AiProcessingMode.CROSS_BORDER);
  }

  @Test
  void currentAiProcessingMode_defaultsWhenNoOrgContext() {
    assertThat(SecurityUtils.currentAiProcessingMode(null)).isEqualTo(AiProcessingMode.DEFAULT);
  }

  @Test
  void currentPlanLimits_returnsPlanLimitsFromOrgContext() {
    PlanLimits planLimits = new PlanLimits("PRO", 100, 1000L);
    OrgContext orgContext = new OrgContext(List.of(), List.of(), planLimits);
    Authentication authentication = mock(Authentication.class);
    when(authentication.getDetails()).thenReturn(orgContext);

    assertThat(SecurityUtils.currentPlanLimits(authentication)).contains(planLimits);
  }

  @Test
  void currentPlanLimits_emptyWhenNoOrgContextOrNoPlanLimits() {
    assertThat(SecurityUtils.currentPlanLimits(null)).isEmpty();

    OrgContext orgContext = new OrgContext(List.of(), List.of(), null);
    Authentication authentication = mock(Authentication.class);
    when(authentication.getDetails()).thenReturn(orgContext);
    assertThat(SecurityUtils.currentPlanLimits(authentication)).isEmpty();
  }
}
