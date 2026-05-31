package com.pravoos.ai.config;

import com.pravoos.ai.security.JwtAuthenticationFilter;
import com.pravoos.ai.security.JwtTokenProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtTokenProvider jwtTokenProvider;

    public SecurityConfig(JwtTokenProvider jwtTokenProvider) {
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/api/ai/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/ai/documents/**").hasRole("ADMIN")
                        .requestMatchers("/api/ai/cases/**").hasAnyRole("LAWYER", "ADMIN")
                        .requestMatchers("/api/ai/workflows/**").hasAnyRole("LAWYER", "ADMIN")
                        .requestMatchers("/api/ai/responses/**").hasAnyRole("LAWYER", "ADMIN")
                        .requestMatchers("/api/ai/messages/**").hasAnyRole("LAWYER", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/ai/conversations/**").hasAnyRole("LAWYER", "ADMIN")
                        .requestMatchers("/api/ai/chat/**").hasAnyRole("LAWYER", "ADMIN")
                        .anyRequest().authenticated()
                )
                .addFilterBefore(new JwtAuthenticationFilter(jwtTokenProvider),
                        UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
