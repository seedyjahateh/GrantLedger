package edu.university.grantledger;

import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;

@SpringBootTest
@AutoConfigureMockMvc
@org.springframework.context.annotation.Import(ContractIT.ContractChecks.class)
public abstract class DatabaseTestSupport {
  protected static final UUID DEPARTMENT = UUID.fromString("10000000-0000-0000-0000-000000000001");
  protected static final UUID OTHER_DEPARTMENT =
      UUID.fromString("10000000-0000-0000-0000-000000000002");
  private static final String EXTERNAL_URL = System.getenv("GL_TEST_DATABASE_URL");
  private static final PostgreSQLContainer<?> POSTGRES;

  static {
    if (EXTERNAL_URL == null) {
      POSTGRES = new PostgreSQLContainer<>("postgres:17.6-alpine");
      POSTGRES.start();
    } else POSTGRES = null;
  }

  @Autowired protected MockMvc mvc;

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry properties) {
    properties.add(
        "spring.datasource.url", () -> EXTERNAL_URL == null ? POSTGRES.getJdbcUrl() : EXTERNAL_URL);
    properties.add(
        "spring.datasource.username",
        () ->
            EXTERNAL_URL == null
                ? POSTGRES.getUsername()
                : System.getenv().getOrDefault("GL_TEST_DATABASE_USER", "postgres"));
    properties.add(
        "spring.datasource.password",
        () ->
            EXTERNAL_URL == null
                ? POSTGRES.getPassword()
                : System.getenv().getOrDefault("GL_TEST_DATABASE_PASSWORD", ""));
    properties.add("spring.flyway.enabled", () -> true);
    properties.add(
        "spring.flyway.user",
        () ->
            EXTERNAL_URL == null
                ? POSTGRES.getUsername()
                : System.getenv().getOrDefault("GL_TEST_DATABASE_USER", "postgres"));
    properties.add(
        "spring.flyway.password",
        () ->
            EXTERNAL_URL == null
                ? POSTGRES.getPassword()
                : System.getenv().getOrDefault("GL_TEST_DATABASE_PASSWORD", ""));
    properties.add(
        "spring.security.oauth2.client.registration.institution.client-secret", () -> "test-only");
    properties.add("logging.level.root", () -> "WARN");
    properties.add("server.servlet.session.cookie.secure", () -> false);
  }
}
