package com.pravoos.ai.core.internal.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class AgentPropertiesBindingTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
          .withUserConfiguration(AgentConfig.class);

  @Test
  void agentLimitsFromConfigurationReachTheBean() {
    contextRunner
        .withPropertyValues(
            "agent.max-iterations=3",
            "agent.max-tool-calls=4",
            "agent.max-result-chars=500",
            "agent.max-arguments-chars=600",
            "agent.max-duration=PT7S",
            "agent.max-total-tokens=9000",
            "agent.proposal-ttl=PT5M",
            "agent.max-write-actions-per-hour=11")
        .run(
            context -> {
              AgentProperties properties = context.getBean(AgentProperties.class);
              assertThat(properties.maxIterations()).isEqualTo(3);
              assertThat(properties.maxToolCalls()).isEqualTo(4);
              assertThat(properties.maxResultChars()).isEqualTo(500);
              assertThat(properties.maxArgumentsChars()).isEqualTo(600);
              assertThat(properties.maxDuration()).isEqualTo(Duration.ofSeconds(7));
              assertThat(properties.maxTotalTokens()).isEqualTo(9000L);
              assertThat(properties.proposalTtl()).isEqualTo(Duration.ofMinutes(5));
              assertThat(properties.maxWriteActionsPerHour()).isEqualTo(11);
            });
  }

  @Test
  void nonPositiveLimitsFallBackToDefaultsInsteadOfDisablingTheGuardrails() {
    contextRunner
        .withPropertyValues(
            "agent.max-iterations=0",
            "agent.max-tool-calls=-1",
            "agent.max-result-chars=0",
            "agent.max-arguments-chars=0",
            "agent.max-duration=PT0S",
            "agent.max-total-tokens=0",
            "agent.max-write-actions-per-hour=0")
        .run(
            context -> {
              AgentProperties properties = context.getBean(AgentProperties.class);
              assertThat(properties.maxIterations()).isEqualTo(8);
              assertThat(properties.maxToolCalls()).isEqualTo(12);
              assertThat(properties.maxResultChars()).isEqualTo(8000);
              assertThat(properties.maxArgumentsChars()).isEqualTo(8000);
              assertThat(properties.maxDuration()).isEqualTo(Duration.ofSeconds(120));
              assertThat(properties.maxTotalTokens()).isEqualTo(120_000L);
              assertThat(properties.maxWriteActionsPerHour()).isEqualTo(30);
            });
  }
}
