package com.pravoos.ai;

import com.pravoos.ai.config.DeepSeekProperties;
import com.pravoos.ai.config.DocumentProperties;
import com.pravoos.ai.config.JwtProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({DeepSeekProperties.class, DocumentProperties.class, JwtProperties.class})
public class AiServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiServiceApplication.class, args);
    }
}
