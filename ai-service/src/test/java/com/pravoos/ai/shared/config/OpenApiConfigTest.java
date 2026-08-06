package com.pravoos.ai.shared.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.junit.jupiter.api.Test;

class OpenApiConfigTest {

  private final OpenApiConfig config = new OpenApiConfig();

  @Test
  void openApiDeclaresBearerSecurityScheme() {
    OpenAPI openApi = config.aiServiceOpenApi();

    assertThat(openApi.getInfo().getTitle()).isEqualTo("PravoOS AI Service API");
    assertThat(openApi.getSecurity()).hasSize(1);
    assertThat(openApi.getSecurity().get(0)).containsKey("bearerAuth");

    SecurityScheme scheme = openApi.getComponents().getSecuritySchemes().get("bearerAuth");
    assertThat(scheme.getType()).isEqualTo(SecurityScheme.Type.HTTP);
    assertThat(scheme.getScheme()).isEqualTo("bearer");
    assertThat(scheme.getBearerFormat()).isEqualTo("JWT");
  }
}
