package com.pravoos.ai.recyclebin.internal.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(RecycleBinProperties.class)
public class RecycleBinConfig {}
