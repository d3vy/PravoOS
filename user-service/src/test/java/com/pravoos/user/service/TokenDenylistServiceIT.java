package com.pravoos.user.service;

import com.pravoos.user.identity.api.TokenDenylistService;
import com.pravoos.user.identity.internal.config.JwtProperties;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class TokenDenylistServiceIT {

    private static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    private static LettuceConnectionFactory connectionFactory;
    private static TokenDenylistService denylistService;

    @BeforeAll
    static void startRedis() {
        REDIS.start();
        connectionFactory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        connectionFactory.afterPropertiesSet();
        connectionFactory.start();
        StringRedisTemplate redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
        denylistService = new TokenDenylistService(redisTemplate, new JwtProperties("", "", 900_000L, 0L));
    }

    @AfterAll
    static void stopRedis() {
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
        REDIS.stop();
    }

    @Test
    void revokesTokensIssuedBeforeCutoffButNotAfter() {
        UUID userId = UUID.randomUUID();
        long now = Instant.now().getEpochSecond();

        assertThat(denylistService.isAccessTokenRevoked(userId.toString(), now)).isFalse();

        denylistService.revokeAccessTokensFor(userId);

        assertThat(denylistService.isAccessTokenRevoked(userId.toString(), now - 100)).isTrue();
        assertThat(denylistService.isAccessTokenRevoked(userId.toString(), now + 100)).isFalse();
    }

    @Test
    void unknownUserIsNotRevoked() {
        assertThat(denylistService.isAccessTokenRevoked(UUID.randomUUID().toString(), Instant.now().getEpochSecond()))
                .isFalse();
    }
}
