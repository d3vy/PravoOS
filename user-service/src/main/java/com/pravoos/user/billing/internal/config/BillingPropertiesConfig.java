package com.pravoos.user.billing.internal.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(YooKassaProperties.class)
public class BillingPropertiesConfig {}
