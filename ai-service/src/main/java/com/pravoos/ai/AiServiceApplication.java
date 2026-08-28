package com.pravoos.ai;

import com.pravoos.ai.shared.config.*;
import com.pravoos.common.security.PiiCryptoProperties;
import com.pravoos.common.web.ClientIpSanitizingFilter;
import com.pravoos.common.web.RequestIdFilter;
import com.pravoos.common.web.UtcTimestampModule;
import org.springframework.beans.factory.annotation.Value;
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
@EnableJpaRepositories(basePackages = "com.pravoos.ai")
@EnableMongoRepositories(basePackages = "com.pravoos.ai")
@EnableScheduling
public class AiServiceApplication {

  public static void main(String[] args) {
    SpringApplication.run(AiServiceApplication.class, args);
  }

  @Bean
  public RequestIdFilter requestIdFilter() {
    return new RequestIdFilter();
  }

  @Bean
  public UtcTimestampModule utcTimestampModule() {
    return new UtcTimestampModule();
  }

  @Bean
  public ClientIpSanitizingFilter clientIpSanitizingFilter(
      @Value("${app.trusted-peers:" + ClientIpSanitizingFilter.DEFAULT_TRUSTED_PEERS + "}")
          String trustedPeers) {
    return new ClientIpSanitizingFilter(trustedPeers);
  }
}
