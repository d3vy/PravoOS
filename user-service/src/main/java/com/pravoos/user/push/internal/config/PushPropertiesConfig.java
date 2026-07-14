package com.pravoos.user.push.internal.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(VapidProperties.class)
public class PushPropertiesConfig {
}
