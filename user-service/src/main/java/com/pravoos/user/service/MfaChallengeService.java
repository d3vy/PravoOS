package com.pravoos.user.service;

import com.pravoos.user.config.MfaProperties;
import com.pravoos.user.exception.MfaException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;

@Service
public class MfaChallengeService {

    private static final String CHALLENGE_KEY_PREFIX = "mfa_challenge:";
    private static final String ATTEMPTS_KEY_PREFIX = "mfa_attempts:";
    private static final int TOKEN_BYTE_LENGTH = 32;

    private final StringRedisTemplate redisTemplate;
    private final MfaProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();

    public MfaChallengeService(StringRedisTemplate redisTemplate, MfaProperties properties) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    public String createChallenge(UUID userId) {
        String token = generateToken();
        redisTemplate.opsForValue().set(challengeKey(token), userId.toString(), properties.challengeTtl());
        return token;
    }

    public UUID resolve(String token) {
        String userId = redisTemplate.opsForValue().get(challengeKey(token));
        if (userId == null) {
            throw MfaException.invalidChallenge();
        }
        return UUID.fromString(userId);
    }

    public void registerFailedAttempt(String token) {
        String attemptsKey = attemptsKey(token);
        Long attempts = redisTemplate.opsForValue().increment(attemptsKey);
        if (attempts != null && attempts == 1L) {
            redisTemplate.expire(attemptsKey, properties.challengeTtl());
        }
        if (attempts != null && attempts >= properties.maxChallengeAttempts()) {
            invalidate(token);
            throw MfaException.invalidChallenge();
        }
    }

    public void invalidate(String token) {
        redisTemplate.delete(challengeKey(token));
        redisTemplate.delete(attemptsKey(token));
    }

    private String generateToken() {
        byte[] buffer = new byte[TOKEN_BYTE_LENGTH];
        secureRandom.nextBytes(buffer);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buffer);
    }

    private String challengeKey(String token) {
        return CHALLENGE_KEY_PREFIX + token;
    }

    private String attemptsKey(String token) {
        return ATTEMPTS_KEY_PREFIX + token;
    }
}
