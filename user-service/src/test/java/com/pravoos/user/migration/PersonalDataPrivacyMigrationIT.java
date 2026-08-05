package com.pravoos.user.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers(disabledWithoutDocker = true)
class PersonalDataPrivacyMigrationIT {

  private static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>(
          DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

  private String schema;
  private PGSimpleDataSource dataSource;

  @BeforeAll
  static void startContainer() {
    POSTGRES.start();
  }

  @AfterAll
  static void stopContainer() {
    POSTGRES.stop();
  }

  @BeforeEach
  void createIsolatedSchema() throws SQLException {
    schema = "t" + UUID.randomUUID().toString().replace("-", "");
    try (Connection bootstrap = plainDataSource().getConnection();
        Statement statement = bootstrap.createStatement()) {
      statement.execute("CREATE SCHEMA " + schema);
    }
    dataSource = plainDataSource();
    dataSource.setCurrentSchema(schema);
  }

  private PGSimpleDataSource plainDataSource() {
    PGSimpleDataSource ds = new PGSimpleDataSource();
    ds.setUrl(POSTGRES.getJdbcUrl());
    ds.setUser(POSTGRES.getUsername());
    ds.setPassword(POSTGRES.getPassword());
    return ds;
  }

  @Test
  void migratesUpToV25WithoutErrors() {
    var result = flyway(null).migrate();

    assertThat(result.success).isTrue();
    assertThat(result.migrations).extracting(migration -> migration.version).contains("25");
    assertThat(flyway(null).info().current().getVersion().toString()).isEqualTo("25");
  }

  @Test
  void widensLegacyVarcharColumnsToText() throws SQLException {
    flyway(null).migrate();

    try (Connection connection = dataSource.getConnection()) {
      assertThat(columnType(connection, "lawyer_profiles", "full_name")).isEqualTo("text");
      assertThat(columnType(connection, "lawyer_profiles", "phone")).isEqualTo("text");
      assertThat(columnType(connection, "lawyer_applications", "full_name")).isEqualTo("text");
      assertThat(columnType(connection, "lawyer_applications", "phone")).isEqualTo("text");
    }
  }

  @Test
  void addsConsentColumnsToLawyerApplicationsWithDefaults() throws SQLException {
    flyway(null).migrate();

    try (Connection connection = dataSource.getConnection();
        Statement statement = connection.createStatement()) {
      statement.execute(
          "INSERT INTO lawyer_applications "
              + "(email, full_name, password_hash, specialization, status, status_token) "
              + "VALUES ('a@example.com', 'A', 'hash', 'Civil', 'PENDING', 'token-1')");

      try (ResultSet rs =
          statement.executeQuery(
              "SELECT consent_cross_border, consent_marketing, consent_policy_version "
                  + "FROM lawyer_applications WHERE email = 'a@example.com'")) {
        assertThat(rs.next()).isTrue();
        assertThat(rs.getBoolean("consent_cross_border")).isFalse();
        assertThat(rs.getBoolean("consent_marketing")).isFalse();
        assertThat(rs.getString("consent_policy_version")).isNull();
      }
    }
  }

  @Test
  void enforcesAtMostOneActiveConsentPerUserAndPurpose() throws SQLException {
    flyway(null).migrate();
    UUID userId = insertUser("active-consent@example.com");

    try (Connection connection = dataSource.getConnection()) {
      insertConsent(connection, userId, "PERSONAL_DATA");

      assertThatThrownBy(() -> insertConsent(connection, userId, "PERSONAL_DATA"))
          .isInstanceOf(SQLException.class);
    }
  }

  @Test
  void allowsNewActiveConsentAfterPreviousOneIsRevoked() throws SQLException {
    flyway(null).migrate();
    UUID userId = insertUser("revoked-consent@example.com");

    try (Connection connection = dataSource.getConnection()) {
      insertConsent(connection, userId, "MARKETING");
      try (PreparedStatement update =
          connection.prepareStatement(
              "UPDATE user_consents SET revoked_at = NOW() "
                  + "WHERE user_id = ? AND purpose = 'MARKETING' AND revoked_at IS NULL")) {
        update.setObject(1, userId);
        assertThat(update.executeUpdate()).isEqualTo(1);
      }

      insertConsent(connection, userId, "MARKETING");

      try (PreparedStatement count =
          connection.prepareStatement(
              "SELECT count(*) FROM user_consents WHERE user_id = ? AND purpose = 'MARKETING'")) {
        count.setObject(1, userId);
        try (ResultSet rs = count.executeQuery()) {
          assertThat(rs.next()).isTrue();
          assertThat(rs.getLong(1)).isEqualTo(2L);
        }
      }
    }
  }

  @Test
  void backfillsLegacyPersonalDataConsentForPreExistingUsers() throws SQLException {
    flyway("24").migrate();

    UUID legacyUserId = insertUser("legacy-user@example.com");

    flyway(null).migrate();

    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement =
            connection.prepareStatement(
                "SELECT purpose, policy_version, source FROM user_consents WHERE user_id = ?")) {
      statement.setObject(1, legacyUserId);
      try (ResultSet rs = statement.executeQuery()) {
        assertThat(rs.next()).isTrue();
        assertThat(rs.getString("purpose")).isEqualTo("PERSONAL_DATA");
        assertThat(rs.getString("policy_version")).isEqualTo("1.0");
        assertThat(rs.getString("source")).isEqualTo("LEGACY");
        assertThat(rs.next()).isFalse();
      }
    }
  }

  @Test
  void createsSubjectRequestsTableWithExpectedDefaults() throws SQLException {
    flyway(null).migrate();
    UUID userId = insertUser("subject-request@example.com");

    try (Connection connection = dataSource.getConnection();
        PreparedStatement insert =
            connection.prepareStatement(
                "INSERT INTO subject_requests (user_id, subject_ref, type, due_at) "
                    + "VALUES (?, ?, 'ERASURE', NOW() + INTERVAL '30 days')")) {
      insert.setObject(1, userId);
      insert.setString(2, userId.toString());
      assertThat(insert.executeUpdate()).isEqualTo(1);
    }

    try (Connection connection = dataSource.getConnection();
        PreparedStatement select =
            connection.prepareStatement(
                "SELECT status, completed_at FROM subject_requests WHERE user_id = ?")) {
      select.setObject(1, userId);
      try (ResultSet rs = select.executeQuery()) {
        assertThat(rs.next()).isTrue();
        assertThat(rs.getString("status")).isEqualTo("PENDING");
        assertThat(rs.getObject("completed_at")).isNull();
      }
    }
  }

  private Flyway flyway(String target) {
    var configuration =
        Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .schemas(schema)
            .defaultSchema(schema);
    if (target != null) {
      configuration.target(target);
    }
    return configuration.load();
  }

  private UUID insertUser(String email) throws SQLException {
    UUID userId = UUID.randomUUID();
    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement =
            connection.prepareStatement(
                "INSERT INTO users (id, email, password_hash, role, status) "
                    + "VALUES (?, ?, 'hash', 'LAWYER', 'ACTIVE')")) {
      statement.setObject(1, userId);
      statement.setString(2, email);
      statement.executeUpdate();
    }
    return userId;
  }

  private void insertConsent(Connection connection, UUID userId, String purpose)
      throws SQLException {
    try (PreparedStatement statement =
        connection.prepareStatement(
            "INSERT INTO user_consents (user_id, purpose, policy_version) VALUES (?, ?, '2.0')")) {
      statement.setObject(1, userId);
      statement.setString(2, purpose);
      statement.executeUpdate();
    }
  }

  private String columnType(Connection connection, String table, String column)
      throws SQLException {
    try (PreparedStatement statement =
        connection.prepareStatement(
            "SELECT data_type FROM information_schema.columns "
                + "WHERE table_schema = ? AND table_name = ? AND column_name = ?")) {
      statement.setString(1, schema);
      statement.setString(2, table);
      statement.setString(3, column);
      try (ResultSet rs = statement.executeQuery()) {
        assertThat(rs.next()).isTrue();
        return rs.getString("data_type");
      }
    }
  }
}
