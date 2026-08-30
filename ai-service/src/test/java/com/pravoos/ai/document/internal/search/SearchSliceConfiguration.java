package com.pravoos.ai.document.internal.search;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;

@SpringBootConfiguration
@EnableAutoConfiguration
@EntityScan(basePackages = "com.pravoos.ai")
public class SearchSliceConfiguration {}
