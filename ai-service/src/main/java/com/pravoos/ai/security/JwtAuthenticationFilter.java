package com.pravoos.ai.security;

import com.pravoos.common.security.JwtVerifier;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Date;
import java.util.List;

public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtVerifier jwtVerifier;
    private final AccessTokenDenylist accessTokenDenylist;

    public JwtAuthenticationFilter(JwtVerifier jwtVerifier,
                                   AccessTokenDenylist accessTokenDenylist) {
        this.jwtVerifier = jwtVerifier;
        this.accessTokenDenylist = accessTokenDenylist;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String token = extractBearerToken(request);

        if (token != null && jwtVerifier.isValid(token)) {
            Claims claims = jwtVerifier.extractClaims(token);
            String role = claims.get("role", String.class);

            if (role != null && !role.isBlank() && !isRevoked(claims)) {
                List<SimpleGrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role));
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(claims.getSubject(), null, authorities);
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }

        chain.doFilter(request, response);
    }

    private boolean isRevoked(Claims claims) {
        Date issuedAt = claims.getIssuedAt();
        long issuedAtSeconds = issuedAt != null ? issuedAt.toInstant().getEpochSecond() : 0L;
        return accessTokenDenylist.isRevoked(claims.getSubject(), issuedAtSeconds);
    }

    private String extractBearerToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }
}
