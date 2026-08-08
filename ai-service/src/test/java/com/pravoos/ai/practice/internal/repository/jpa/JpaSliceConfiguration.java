package com.pravoos.ai.practice.internal.repository.jpa;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootConfiguration
@EnableAutoConfiguration
@EnableJpaRepositories(basePackageClasses = InvoiceRepository.class)
@EntityScan(basePackages = "com.pravoos.ai")
public class JpaSliceConfiguration {}
