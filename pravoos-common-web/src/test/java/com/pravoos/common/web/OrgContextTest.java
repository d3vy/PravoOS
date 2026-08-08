package com.pravoos.common.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OrgContextTest {

  @Test
  void nullOrgIdsAndClientIds_defaultToEmptyList() {
    OrgContext context = new OrgContext(null, null, null, null);

    assertThat(context.orgIds()).isEmpty();
    assertThat(context.clientIds()).isEmpty();
  }

  @Test
  void nullAiProcessingMode_defaultsToDefaultMode() {
    OrgContext context = new OrgContext(List.of(), List.of(), null, null);

    assertThat(context.aiProcessingMode()).isEqualTo(AiProcessingMode.DEFAULT);
  }

  @Test
  void orgIdsAndClientIds_areDefensivelyCopiedAndImmutable() {
    List<UUID> mutableOrgIds = new java.util.ArrayList<>(List.of(UUID.randomUUID()));

    OrgContext context = new OrgContext(mutableOrgIds, List.of(), null, null);
    mutableOrgIds.add(UUID.randomUUID());

    assertThat(context.orgIds()).hasSize(1);
    assertThatThrownBy(() -> context.orgIds().add(UUID.randomUUID()))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void singleArgConstructor_defaultsClientIdsPlanLimitsAndAiMode() {
    List<UUID> orgIds = List.of(UUID.randomUUID());

    OrgContext context = new OrgContext(orgIds);

    assertThat(context.orgIds()).isEqualTo(orgIds);
    assertThat(context.clientIds()).isEmpty();
    assertThat(context.planLimits()).isNull();
    assertThat(context.aiProcessingMode()).isEqualTo(AiProcessingMode.DEFAULT);
  }

  @Test
  void twoArgConstructor_setsOrgAndClientIds() {
    List<UUID> orgIds = List.of(UUID.randomUUID());
    List<UUID> clientIds = List.of(UUID.randomUUID());

    OrgContext context = new OrgContext(orgIds, clientIds);

    assertThat(context.orgIds()).isEqualTo(orgIds);
    assertThat(context.clientIds()).isEqualTo(clientIds);
    assertThat(context.aiProcessingMode()).isEqualTo(AiProcessingMode.DEFAULT);
  }

  @Test
  void threeArgConstructor_setsPlanLimits() {
    PlanLimits planLimits = new PlanLimits("PRO", 100, 1000L);

    OrgContext context = new OrgContext(List.of(), List.of(), planLimits);

    assertThat(context.planLimits()).isEqualTo(planLimits);
    assertThat(context.aiProcessingMode()).isEqualTo(AiProcessingMode.DEFAULT);
  }
}
