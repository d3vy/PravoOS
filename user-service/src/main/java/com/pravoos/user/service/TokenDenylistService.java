package com.pravoos.user.service;

import com.pravoos.user.config.JwtProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
public class TokenDenylistService {

    private static final Logger log = LoggerFactory.getLogger(TokenDenylistService.class);
    private static final String KEY_PREFIX = "auth:revoked_after:";

    private final StringRedisTemplate redisTemplate;
    private final Duration accessTokenTtl;

    public TokenDenylistService(StringRedisTemplate redisTemplate, JwtProperties jwtProperties) {
        this.redisTemplate = redisTemplate;
        this.accessTokenTtl = Duration.ofMillis(jwtProperties.accessExpirationMs());
    }

    public void revokeAccessTokensFor(UUID userId) {
        String key = KEY_PREFIX + userId;
        long cutoffEpochSeconds = Instant.now().getEpochSecond();
        try {
            redisTemplate.opsForValue().set(key, Long.toString(cutoffEpochSeconds), accessTokenTtl);
            log.info("Access tokens revoked for user {} (issued before {})", userId, cutoffEpochSeconds);
        } catch (DataAccessException ex) {
            log.error("Failed to write access-token denylist entry for user {}", userId, ex);
        }
    }
}
