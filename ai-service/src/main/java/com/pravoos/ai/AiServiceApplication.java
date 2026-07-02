package com.pravoos.ai;

import com.pravoos.ai.config.ArbitrProperties;
import com.pravoos.ai.config.DocumentProperties;
import com.pravoos.ai.config.FileCryptoProperties;
import com.pravoos.ai.config.JwtProperties;
import com.pravoos.ai.config.MalwareScanProperties;
import com.pravoos.ai.config.OpenAiProperties;
import com.pravoos.common.web.RequestIdFilter;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@EnableConfigurationProperties({OpenAiProperties.class, DocumentProperties.class, JwtProperties.class,
        ArbitrProperties.class, FileCryptoProperties.class, MalwareScanProperties.class})
@EnableJpaRepositories(basePackages = "com.pravoos.ai.repository.jpa")
@EnableMongoRepositories(basePackages = "com.pravoos.ai.repository.mongo")
@EnableScheduling
public class AiServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiServiceApplication.class, args);
    }

    @Bean
    public RequestIdFilter requestIdFilter() {
        return new RequestIdFilter();
    }
}
