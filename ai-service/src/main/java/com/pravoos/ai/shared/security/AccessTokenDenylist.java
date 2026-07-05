package com.pravoos.ai.shared.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class AccessTokenDenylist {

    private static final Logger log = LoggerFactory.getLogger(AccessTokenDenylist.class);
    private static final String KEY_PREFIX = "auth:revoked_after:";

    private final StringRedisTemplate redisTemplate;

    public AccessTokenDenylist(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public boolean isRevoked(String userId, long issuedAtEpochSeconds) {
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
