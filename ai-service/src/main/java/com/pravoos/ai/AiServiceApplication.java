package com.pravoos.ai;

import com.pravoos.ai.config.ArbitrProperties;
import com.pravoos.ai.config.OpenAiProperties;
import com.pravoos.ai.config.DocumentProperties;
import com.pravoos.ai.config.JwtProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@EnableConfigurationProperties({OpenAiProperties.class, DocumentProperties.class, JwtProperties.class, ArbitrProperties.class})
@EnableJpaRepositories(basePackages = "com.pravoos.ai.repository.jpa")
@EnableMongoRepositories(basePackages = "com.pravoos.ai.repository.mongo")
@EnableScheduling
public class AiServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiServiceApplication.class, args);
    }
}
