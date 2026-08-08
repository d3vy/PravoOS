package com.pravoos.ai.shared.repository.jpa;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootConfiguration
@EnableAutoConfiguration
@EnableJpaRepositories(basePackageClasses = OutboxEventRepository.class)
@EntityScan(basePackages = "com.pravoos.ai")
public class JpaSliceConfiguration {}
