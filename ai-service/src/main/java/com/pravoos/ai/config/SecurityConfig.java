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
                        .requestMatchers("/api/ai/cases/**").hasRole("LAWYER")
                        .requestMatchers("/api/ai/clients/**").hasRole("LAWYER")
                        .requestMatchers("/api/ai/drafts/**").hasRole("LAWYER")
                        .requestMatchers("/api/ai/draft-types").hasRole("LAWYER")
                        .requestMatchers("/api/ai/workflows/**").hasRole("LAWYER")
                        .requestMatchers("/api/ai/responses/**").hasRole("LAWYER")
                        .requestMatchers("/api/ai/messages/**").hasRole("LAWYER")
                        .requestMatchers(HttpMethod.GET, "/api/ai/conversations/**").hasRole("LAWYER")
                        .requestMatchers("/api/ai/chat/**").hasRole("LAWYER")
                        .anyRequest().authenticated()
                )
                .addFilterBefore(new JwtAuthenticationFilter(jwtTokenProvider),
                        UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
