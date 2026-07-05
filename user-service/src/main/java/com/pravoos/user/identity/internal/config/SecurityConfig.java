package com.pravoos.user.identity.internal.config;

import com.pravoos.user.identity.internal.config.JwtProperties;
import com.pravoos.common.security.JwtVerifier;
import com.pravoos.user.shared.security.InternalSecretFilter;
import com.pravoos.user.shared.security.InternalSecretVerifier;
import com.pravoos.user.identity.internal.security.JwtAuthenticationFilter;
import com.pravoos.user.identity.api.TokenDenylistService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private static final int BCRYPT_STRENGTH = 12;

    private final TokenDenylistService tokenDenylistService;
    private final InternalSecretVerifier internalSecretVerifier;

    public SecurityConfig(TokenDenylistService tokenDenylistService,
                          InternalSecretVerifier internalSecretVerifier) {
        this.tokenDenylistService = tokenDenylistService;
        this.internalSecretVerifier = internalSecretVerifier;
    }

    @Bean
    public JwtVerifier jwtVerifier(JwtProperties jwtProperties) {
        return new JwtVerifier(jwtProperties.publicKey());
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtVerifier jwtVerifier) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**", "/actuator/health", "/actuator/health/**", "/actuator/prometheus").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/internal/**").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated()
                )
                .addFilterBefore(new InternalSecretFilter(internalSecretVerifier),
                        UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(new JwtAuthenticationFilter(jwtVerifier, tokenDenylistService),
                        UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(BCRYPT_STRENGTH);
    }
}
