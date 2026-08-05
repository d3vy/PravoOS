package com.pravoos.user.privacy.internal.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(PrivacyProperties.class)
class PrivacyPropertiesConfig {}
