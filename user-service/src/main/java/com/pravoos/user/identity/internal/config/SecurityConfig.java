package com.pravoos.user.identity.internal.config;

import com.pravoos.common.security.JwtVerifier;
import com.pravoos.common.security.internal.InternalCallerVerifier;
import com.pravoos.common.web.internal.InternalCallerSecurityConfiguration;
import com.pravoos.common.web.internal.InternalSecretFilter;
import com.pravoos.user.identity.api.TokenDenylistService;
import com.pravoos.user.identity.internal.security.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@Import(InternalCallerSecurityConfiguration.class)
public class SecurityConfig {

  private static final int BCRYPT_STRENGTH = 12;

  private final TokenDenylistService tokenDenylistService;
  private final InternalCallerVerifier internalCallerVerifier;

  public SecurityConfig(
      TokenDenylistService tokenDenylistService, InternalCallerVerifier internalCallerVerifier) {
    this.tokenDenylistService = tokenDenylistService;
    this.internalCallerVerifier = internalCallerVerifier;
  }

  @Bean
  public JwtVerifier jwtVerifier(JwtProperties jwtProperties) {
    return new JwtVerifier(jwtProperties.publicKey());
  }

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtVerifier jwtVerifier)
      throws Exception {
    return http.csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .exceptionHandling(
            exceptions ->
                exceptions.authenticationEntryPoint(
                    new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(
                        "/api/auth/**",
                        "/actuator/health",
                        "/actuator/health/**",
                        "/actuator/prometheus")
                    .permitAll()
                    .requestMatchers("/api/billing/webhook")
                    .permitAll()
                    .requestMatchers("/api/user/privacy/policy")
                    .permitAll()
                    .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                    .permitAll()
                    .requestMatchers("/internal/**")
                    .permitAll()
                    .requestMatchers("/api/admin/**")
                    .hasRole("ADMIN")
                    .requestMatchers("/api/user/billing/**")
                    .hasAnyRole("LAWYER", "ADMIN")
                    .anyRequest()
                    .authenticated())
        .addFilterBefore(
            new InternalSecretFilter(internalCallerVerifier),
            UsernamePasswordAuthenticationFilter.class)
        .addFilterBefore(
            new JwtAuthenticationFilter(jwtVerifier, tokenDenylistService),
            UsernamePasswordAuthenticationFilter.class)
        .build();
  }

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(BCRYPT_STRENGTH);
  }
}
