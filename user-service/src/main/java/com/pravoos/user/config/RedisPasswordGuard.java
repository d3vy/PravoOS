package com.pravoos.user.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import jakarta.annotation.PostConstruct;

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
                            + "Redis stores token denylist and brute-force counters and must not run without a password.");
        }
    }
}
