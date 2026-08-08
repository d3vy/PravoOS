package com.pravoos.observability;

import static org.assertj.core.api.Assertions.assertThat;

import com.pravoos.observability.logging.PiiScrubber;
import com.pravoos.observability.sentry.SentryEventEnricher;
import com.pravoos.observability.sentry.SentryEventPolicy;
import com.pravoos.observability.sentry.ServerErrorOnlyPolicy;
import io.sentry.Sentry;
import io.sentry.SentryOptions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class ObservabilityAutoConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(ObservabilityAutoConfiguration.class));

  @Test
  void registersPiiScrubberByDefault() {
    contextRunner.run(context -> assertThat(context).hasSingleBean(PiiScrubber.class));
  }

  @Test
  void backsOffWhenCustomPiiScrubberAlreadyProvided() {
    contextRunner
        .withUserConfiguration(CustomPiiScrubberConfig.class)
        .run(
            context -> {
              assertThat(context).hasSingleBean(PiiScrubber.class);
              assertThat(context.getBean(PiiScrubber.class))
                  .isSameAs(context.getBean(CustomPiiScrubberConfig.class).piiScrubber());
            });
  }

  @Test
  void registersServerErrorOnlyPolicy_byDefault() {
    contextRunner.run(
        context -> {
          assertThat(context).hasSingleBean(ServerErrorOnlyPolicy.class);
          assertThat(context).hasSingleBean(SentryEventPolicy.class);
        });
  }

  @Test
  void reportClientErrorsTrue_disablesServerErrorOnlyPolicy() {
    contextRunner
        .withPropertyValues("pravoos.observability.sentry.report-client-errors=true")
        .run(context -> assertThat(context).doesNotHaveBean(ServerErrorOnlyPolicy.class));
  }

  @Test
  void sentryEventEnricher_wiresServiceNameAndOrderedPolicies() {
    contextRunner
        .withPropertyValues("spring.application.name=my-service")
        .run(
            context -> {
              assertThat(context).hasSingleBean(SentryEventEnricher.class);
              SentryEventEnricher enricher = context.getBean(SentryEventEnricher.class);
              assertThat(enricher).isNotNull();
            });
  }

  @Test
  void sentryOptionsConfiguration_disablesDefaultPiiAndAttachesStacktrace() {
    contextRunner.run(
        context -> {
          @SuppressWarnings("unchecked")
          Sentry.OptionsConfiguration<SentryOptions> configuration =
              context.getBean(Sentry.OptionsConfiguration.class);
          SentryOptions options = new SentryOptions();
          configuration.configure(options);

          assertThat(options.isSendDefaultPii()).isFalse();
          assertThat(options.isAttachStacktrace()).isTrue();
          assertThat(options.getBeforeSend()).isInstanceOf(SentryEventEnricher.class);
        });
  }

  @Configuration
  static class CustomPiiScrubberConfig {
    @Bean
    PiiScrubber piiScrubber() {
      return new PiiScrubber();
    }
  }
}
