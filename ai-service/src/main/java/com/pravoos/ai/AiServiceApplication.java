package com.pravoos.ai;

import com.pravoos.ai.shared.config.*;
import com.pravoos.common.security.PiiCryptoProperties;
import com.pravoos.common.web.RequestIdFilter;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@EnableConfigurationProperties({
  LlmServiceProperties.class,
  DocumentProperties.class,
  DocumentSummaryProperties.class,
  JwtProperties.class,
  ArbitrProperties.class,
  FileCryptoProperties.class,
  MalwareScanProperties.class,
  UploadGuardProperties.class,
  ContractReviewProperties.class,
  DocumentComparisonProperties.class,
  CitationCheckProperties.class,
  UserServiceProperties.class,
  DraftEditingProperties.class,
  HybridSearchProperties.class,
  SignatureProperties.class,
  TabularReviewProperties.class,
  PiiCryptoProperties.class,
  PersonalDataConsentProperties.class,
  InvoicePaymentProperties.class,
  MailboxProperties.class,
  MailSyncProperties.class,
  MailAttachmentProperties.class
})
@EnableJpaRepositories(
    basePackages = {
      "com.pravoos.ai.shared.repository.jpa",
      "com.pravoos.ai.core.internal.repository.jpa",
      "com.pravoos.ai.document.internal.repository.jpa",
      "com.pravoos.ai.practice.internal.repository.jpa"
    })
@EnableMongoRepositories(basePackages = "com.pravoos.ai.core.internal.repository.mongo")
@EnableScheduling
public class AiServiceApplication {

  public static void main(String[] args) {
    SpringApplication.run(AiServiceApplication.class, args);
  }

  @Bean
  public RequestIdFilter requestIdFilter() {
    return new RequestIdFilter();
  }
}
