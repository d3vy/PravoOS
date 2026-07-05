package com.pravoos.user.identity.api;

import com.pravoos.user.identity.internal.config.JwtProperties;
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

    public boolean isAccessTokenRevoked(String userId, long issuedAtEpochSeconds) {
        if (userId == null) {
            return false;
        }
        try {
            String cutoff = redisTemplate.opsForValue().get(KEY_PREFIX + userId);
            return cutoff != null && issuedAtEpochSeconds <= Long.parseLong(cutoff.trim());
        } catch (DataAccessException | NumberFormatException ex) {
            log.warn("Failed to read access-token denylist for user {}", userId, ex);
            return false;
        }
    }
}
