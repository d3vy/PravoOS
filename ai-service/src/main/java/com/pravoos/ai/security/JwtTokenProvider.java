package com.pravoos.ai.security;

import com.pravoos.ai.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import org.springframework.stereotype.Component;

import java.security.interfaces.RSAPublicKey;

@Component
public class JwtTokenProvider {

    private final RSAPublicKey publicKey;

    public JwtTokenProvider(JwtProperties jwtProperties) {
        this.publicKey = RsaKeyLoader.loadPublicKey(jwtProperties.publicKey());
    }

    public Claims extractClaims(String token) {
        return Jwts.parser()
                .verifyWith(publicKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean isTokenValid(String token) {
        try {
            extractClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }
}
