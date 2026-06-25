package com.pravoos.gateway.config;

import com.pravoos.common.config.RedisPasswordCheck;
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
        RedisPasswordCheck.requirePassword(redisPassword,
                "Redis backs the gateway rate limiter and must not run without a password.");
    }
}
