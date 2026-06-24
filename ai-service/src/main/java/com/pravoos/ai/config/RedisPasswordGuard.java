package com.pravoos.ai.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("docker")
public class RedisPasswordGuard {

    private final String redisPassword;

    public RedisPasswordGuard(@Value("${spring.data.redis.password:}") String redisPassword) {
        this.redisPassword = redisPassword;
    }

    @PostConstruct
    void verifyRedisPasswordPresent() {
        if (redisPassword == null || redisPassword.isBlank()) {
            throw new IllegalStateException(
                    "REDIS_PASSWORD must be set in production (profile 'docker'). "
                            + "Redis backs the access-token denylist and scheduler locks and must not run without a password.");
        }
    }
}
