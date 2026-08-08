package com.pravoos.gateway.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.cloud.gateway.config.GatewayProperties;
import org.springframework.cloud.gateway.filter.FilterDefinition;
import org.springframework.cloud.gateway.handler.predicate.PredicateDefinition;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;

class RouteConfigTest {

  private static GatewayProperties gatewayProperties;

  @BeforeAll
  static void loadRoutesFromApplicationYaml() throws IOException {
    List<PropertySource<?>> propertySources =
        new YamlPropertySourceLoader()
            .load(
                "application",
                new org.springframework.core.io.ClassPathResource("application.yml"));
    StandardEnvironment environment = new StandardEnvironment();
    propertySources.forEach(environment.getPropertySources()::addFirst);
    gatewayProperties =
        Binder.get(environment).bind("spring.cloud.gateway", GatewayProperties.class).get();
  }

  @Test
  void definesExactlyExpectedRouteIds() {
    List<String> routeIds =
        gatewayProperties.getRoutes().stream().map(RouteDefinition::getId).toList();

    assertThat(routeIds)
        .containsExactlyInAnyOrder(
            "user-service-auth",
            "user-service-billing-webhook",
            "user-service-admin",
            "user-service-user",
            "ai-service");
  }

  @Test
  void authRoute_isRateLimitedButNotJwtProtected() {
    RouteDefinition route = routeById("user-service-auth");

    assertThat(predicate(route, "Path").getArgs().values()).contains("/api/auth/**");
    assertThat(filterNames(route)).containsExactly("RequestRateLimiter");
  }

  @Test
  void billingWebhookRoute_isRateLimitedButNotJwtProtected() {
    RouteDefinition route = routeById("user-service-billing-webhook");

    assertThat(predicate(route, "Path").getArgs().values()).contains("/api/billing/webhook");
    assertThat(filterNames(route)).containsExactly("RequestRateLimiter");
  }

  @Test
  void adminRoute_requiresAdminRoleBeforeRateLimiting() {
    RouteDefinition route = routeById("user-service-admin");

    assertThat(predicate(route, "Path").getArgs().values()).contains("/api/admin/**");
    assertThat(filterNames(route)).containsExactly("JwtAuthFilter", "RequestRateLimiter");
    FilterDefinition jwtFilter = route.getFilters().get(0);
    assertThat(jwtFilter.getArgs()).containsEntry("requiredRole", "ADMIN");
  }

  @Test
  void userRoute_requiresAnyAuthenticatedUser() {
    RouteDefinition route = routeById("user-service-user");

    assertThat(predicate(route, "Path").getArgs().values()).contains("/api/user/**");
    assertThat(filterNames(route)).containsExactly("JwtAuthFilter", "RequestRateLimiter");
    FilterDefinition jwtFilter = route.getFilters().get(0);
    assertThat(jwtFilter.getArgs()).doesNotContainKey("requiredRole");
  }

  @Test
  void aiServiceRoute_requiresAnyAuthenticatedUser() {
    RouteDefinition route = routeById("ai-service");

    assertThat(predicate(route, "Path").getArgs().values()).contains("/api/ai/**");
    assertThat(filterNames(route)).containsExactly("JwtAuthFilter", "RequestRateLimiter");
  }

  @Test
  void defaultFilters_dedupeCorsHeadersOnEveryRoute() {
    List<FilterDefinition> defaultFilters = gatewayProperties.getDefaultFilters();

    assertThat(defaultFilters).hasSize(1);
    assertThat(defaultFilters.get(0).getName()).isEqualTo("DedupeResponseHeader");
    assertThat(defaultFilters.get(0).getArgs().values())
        .contains("Access-Control-Allow-Credentials Access-Control-Allow-Origin");
  }

  private RouteDefinition routeById(String id) {
    return gatewayProperties.getRoutes().stream()
        .filter(route -> route.getId().equals(id))
        .findFirst()
        .orElseThrow(() -> new AssertionError("Route not found: " + id));
  }

  private PredicateDefinition predicate(RouteDefinition route, String name) {
    return route.getPredicates().stream()
        .filter(predicate -> predicate.getName().equals(name))
        .findFirst()
        .orElseThrow(() -> new AssertionError("Predicate not found: " + name));
  }

  private List<String> filterNames(RouteDefinition route) {
    return route.getFilters().stream().map(FilterDefinition::getName).toList();
  }
}
