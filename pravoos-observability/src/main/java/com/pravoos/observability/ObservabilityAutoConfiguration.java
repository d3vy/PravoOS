package com.pravoos.observability;

import com.pravoos.observability.logging.PiiScrubber;
import com.pravoos.observability.sentry.SentryEventEnricher;
import com.pravoos.observability.sentry.SentryEventPolicy;
import com.pravoos.observability.sentry.ServerErrorOnlyPolicy;
import io.sentry.Sentry;
import io.sentry.SentryOptions;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

import java.util.List;

@AutoConfiguration
@ConditionalOnClass(SentryOptions.class)
public class ObservabilityAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public PiiScrubber piiScrubber() {
        return new PiiScrubber();
    }

    @Bean
    @ConditionalOnProperty(prefix = "pravoos.observability.sentry", name = "report-client-errors",
            havingValue = "false", matchIfMissing = true)
    public SentryEventPolicy serverErrorOnlyPolicy() {
        return new ServerErrorOnlyPolicy();
    }

    @Bean
    public SentryEventEnricher sentryEventEnricher(@Value("${spring.application.name:pravoos}") String serviceName,
                                                   PiiScrubber piiScrubber,
                                                   ObjectProvider<SentryEventPolicy> policies) {
        List<SentryEventPolicy> orderedPolicies = policies.orderedStream().toList();
        return new SentryEventEnricher(serviceName, piiScrubber, orderedPolicies);
    }

    @Bean
    public Sentry.OptionsConfiguration<SentryOptions> pravoosSentryOptionsConfiguration(SentryEventEnricher enricher) {
        return options -> {
            options.setBeforeSend(enricher);
            options.setSendDefaultPii(false);
            options.setAttachStacktrace(true);
        };
    }
}
