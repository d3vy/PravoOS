package com.pravoos.ai;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

import static org.assertj.core.api.Assertions.assertThat;

class ModularityTests {

    private final ApplicationModules modules = ApplicationModules.of(AiServiceApplication.class);

    @Test
    void bootstrapsModuleModel() {
        assertThat(modules.stream()).isNotEmpty();
    }

    @Test
    void writesModuleDocumentation() {
        new Documenter(modules)
                .writeModulesAsPlantUml()
                .writeIndividualModulesAsPlantUml();
    }
}
