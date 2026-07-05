package com.pravoos.user.identity.internal.security;

import com.pravoos.common.security.RsaKeyLoader;
import com.pravoos.user.identity.internal.config.JwtProperties;
import com.pravoos.user.model.enums.UserRole;
import io.jsonwebtoken.Jwts;
import org.springframework.stereotype.Component;

import java.security.interfaces.RSAPrivateKey;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Component
public class JwtTokenProvider {

    private final RSAPrivateKey privateKey;
    private final long accessExpirationMs;

    public JwtTokenProvider(JwtProperties jwtProperties) {
        this.privateKey = RsaKeyLoader.loadPrivateKey(jwtProperties.privateKey());
        this.accessExpirationMs = jwtProperties.accessExpirationMs();
    }

    public String generateToken(UUID userId, String email, UserRole role, List<UUID> orgIds, List<UUID> clientIds) {
        var builder = Jwts.builder()
                .subject(userId.toString())
                .claim("email", email)
                .claim("role", role.name());
        if (orgIds != null && !orgIds.isEmpty()) {
            builder.claim("orgs", orgIds.stream().map(UUID::toString).toList());
        }
        if (clientIds != null && !clientIds.isEmpty()) {
            builder.claim("clients", clientIds.stream().map(UUID::toString).toList());
        }
        return builder
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + accessExpirationMs))
                .signWith(privateKey)
                .compact();
    }
}
