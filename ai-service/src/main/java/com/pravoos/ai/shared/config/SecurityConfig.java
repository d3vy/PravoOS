package com.pravoos.ai.shared.config;

import com.pravoos.ai.shared.security.AccessTokenDenylist;
import com.pravoos.ai.shared.security.JwtAuthenticationFilter;
import com.pravoos.common.security.JwtVerifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

  private final AccessTokenDenylist accessTokenDenylist;

  public SecurityConfig(AccessTokenDenylist accessTokenDenylist) {
    this.accessTokenDenylist = accessTokenDenylist;
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
                        "/actuator/health", "/actuator/health/**", "/actuator/prometheus")
                    .permitAll()
                    .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                    .permitAll()
                    .requestMatchers("/api/ai/billing/invoice-webhook")
                    .permitAll()
                    .requestMatchers("/api/ai/admin/**")
                    .hasRole("ADMIN")
                    .requestMatchers("/api/ai/documents/**")
                    .hasRole("ADMIN")
                    .requestMatchers("/api/ai/document-insights/**")
                    .hasAnyRole("LAWYER", "ADMIN")
                    .requestMatchers("/api/ai/portal/**")
                    .hasRole("CLIENT")
                    .requestMatchers("/api/ai/cases/**")
                    .hasRole("LAWYER")
                    .requestMatchers("/api/ai/dashboard/**")
                    .hasRole("LAWYER")
                    .requestMatchers("/api/ai/calendar/**")
                    .hasRole("LAWYER")
                    .requestMatchers("/api/ai/clients/**")
                    .hasRole("LAWYER")
                    .requestMatchers("/api/ai/templates/**")
                    .hasRole("LAWYER")
                    .requestMatchers("/api/ai/search/**")
                    .hasRole("LAWYER")
                    .requestMatchers("/api/ai/drafts/**")
                    .hasRole("LAWYER")
                    .requestMatchers("/api/ai/draft-types")
                    .hasRole("LAWYER")
                    .requestMatchers("/api/ai/workflows/**")
                    .hasRole("LAWYER")
                    .requestMatchers("/api/ai/workflow-definitions/**")
                    .hasRole("LAWYER")
                    .requestMatchers("/api/ai/contract-reviews/**")
                    .hasRole("LAWYER")
                    .requestMatchers("/api/ai/document-comparisons/**")
                    .hasRole("LAWYER")
                    .requestMatchers("/api/ai/tabular-reviews/**")
                    .hasRole("LAWYER")
                    .requestMatchers("/api/ai/citation-checks/**")
                    .hasRole("LAWYER")
                    .requestMatchers("/api/ai/responses/**")
                    .hasRole("LAWYER")
                    .requestMatchers("/api/ai/messages/**")
                    .hasAnyRole("LAWYER", "ADMIN")
                    .requestMatchers("/api/ai/conversations/**")
                    .hasAnyRole("LAWYER", "ADMIN")
                    .requestMatchers("/api/ai/chat/**")
                    .hasAnyRole("LAWYER", "ADMIN")
                    .requestMatchers("/api/ai/invoices/**")
                    .hasRole("LAWYER")
                    .requestMatchers("/api/ai/billing-profile/**")
                    .hasRole("LAWYER")
                    .requestMatchers("/api/ai/saved-views/**")
                    .hasRole("LAWYER")
                    .requestMatchers("/api/ai/time/**")
                    .hasRole("LAWYER")
                    .requestMatchers("/api/ai/mailboxes/**")
                    .hasRole("LAWYER")
                    .requestMatchers("/api/ai/emails/**")
                    .hasRole("LAWYER")
                    .anyRequest()
                    .hasAnyRole("LAWYER", "ADMIN"))
        .addFilterBefore(
            new JwtAuthenticationFilter(jwtVerifier, accessTokenDenylist),
            UsernamePasswordAuthenticationFilter.class)
        .build();
  }
}
