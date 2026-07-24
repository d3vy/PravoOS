package com.pravoos.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

class ModularityTests {

  private final ApplicationModules modules = ApplicationModules.of(AiServiceApplication.class);

  @Test
  void bootstrapsModuleModel() {
    assertThat(modules.stream()).isNotEmpty();
  }

  @Test
  void verifiesModuleBoundaries() {
    modules.verify();
  }

  @Test
  void writesModuleDocumentation() {
    new Documenter(modules).writeModulesAsPlantUml().writeIndividualModulesAsPlantUml();
  }
}
