package com.pravoos.ai.recyclebin.internal.repository.jpa;

import com.pravoos.ai.document.internal.repository.jpa.DocumentRepository;
import com.pravoos.ai.practice.internal.repository.jpa.CaseRepository;
import com.pravoos.ai.practice.internal.repository.jpa.ClientRepository;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootConfiguration
@EnableAutoConfiguration
@EnableJpaRepositories(
    basePackageClasses = {CaseRepository.class, ClientRepository.class, DocumentRepository.class})
@EntityScan(basePackages = "com.pravoos.ai")
public class JpaSliceConfiguration {}
