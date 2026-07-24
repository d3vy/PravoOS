package com.pravoos.user.collaboration.internal.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(TelegramProperties.class)
public class CollaborationPropertiesConfig {}
