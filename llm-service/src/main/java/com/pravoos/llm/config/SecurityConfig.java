package com.pravoos.llm.config;

import com.pravoos.common.security.internal.InternalCallerVerifier;
import com.pravoos.common.web.internal.InternalCallerSecurityConfiguration;
import com.pravoos.common.web.internal.InternalSecretFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@Import(InternalCallerSecurityConfiguration.class)
public class SecurityConfig {

  private final InternalCallerVerifier internalCallerVerifier;

  public SecurityConfig(InternalCallerVerifier internalCallerVerifier) {
    this.internalCallerVerifier = internalCallerVerifier;
  }

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    return http.csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(
                        "/actuator/health", "/actuator/health/**", "/actuator/prometheus")
                    .permitAll()
                    .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                    .permitAll()
                    .requestMatchers("/internal/**")
                    .permitAll()
                    .anyRequest()
                    .denyAll())
        .addFilterBefore(
            new InternalSecretFilter(internalCallerVerifier),
            UsernamePasswordAuthenticationFilter.class)
        .build();
  }
}
