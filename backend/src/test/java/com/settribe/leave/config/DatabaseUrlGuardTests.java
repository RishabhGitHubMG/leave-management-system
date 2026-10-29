package com.settribe.leave.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class DatabaseUrlGuardTests {

    private static final String POSTGRES_URL = "jdbc:postgresql://127.0.0.1:1/leave";
    private static final String MYSQL_URL = "jdbc:mysql://127.0.0.1:1/leave";
    private static final String H2_URL = "jdbc:h2:mem:leave_check";
    private static final String VALID_SECRET = "a".repeat(32);

    private final DatabaseUrlGuard guard = new DatabaseUrlGuard();

    @Test
    void rejectsRenderWithLocalProfileAndH2() {
        MockEnvironment environment = environment(H2_URL, VALID_SECRET, true, "local");

        assertMessage(environment, "DB_URL points to H2, which is not allowed on Render.");
    }

    @Test
    void rejectsRenderWithTestProfileAndH2() {
        MockEnvironment environment = environment(H2_URL, VALID_SECRET, true, "test");

        assertMessage(environment, "DB_URL points to H2, which is not allowed on Render.");
    }

    @Test
    void rejectsRenderWithoutProfileAndH2() {
        MockEnvironment environment = environment(H2_URL, VALID_SECRET, true);

        assertMessage(environment, "DB_URL points to H2, which is not allowed on Render.");
    }

    @Test
    void acceptsRenderWithValidPostgresAndCredentials() {
        MockEnvironment environment = environment(POSTGRES_URL, VALID_SECRET, true, "render");

        assertDoesNotThrow(() -> guard.postProcessEnvironment(environment, null));
    }

    @Test
    void rejectsMysqlUrlOnRender() {
        MockEnvironment environment = environment(MYSQL_URL, VALID_SECRET, true, "render");

        assertMessage(environment, "DB_URL must be a jdbc:postgresql URL on Render and for the render and docker profiles.");
    }

    @Test
    void rejectsMysqlUrlWithDockerProfile() {
        MockEnvironment environment = environment(MYSQL_URL, VALID_SECRET, false, "docker");

        assertMessage(environment, "DB_URL must be a jdbc:postgresql URL on Render and for the render and docker profiles.");
    }

    @Test
    void rejectsMissingDbUrl() {
        MockEnvironment environment = environment("${DB_URL}", VALID_SECRET, true, "render");

        assertMessage(environment, "DB_URL is required: set it to a JDBC URL for the database.");
    }

    @Test
    void rejectsMissingJwtSecret() {
        MockEnvironment environment = environment(POSTGRES_URL, "${JWT_SECRET}", true, "render");

        assertMessage(environment, "JWT_SECRET is required: set it to a secret of at least 32 bytes.");
    }

    @Test
    void rejectsJwtSecretShorterThan32BytesWithoutEchoingIt() {
        String shortSecret = "short-dummy-secret-0123456789ab";
        assertEquals(31, shortSecret.getBytes(StandardCharsets.UTF_8).length);
        MockEnvironment environment = environment(POSTGRES_URL, shortSecret, true, "render");

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> guard.postProcessEnvironment(environment, null));

        assertEquals("JWT_SECRET must be at least 32 UTF-8 bytes.", error.getMessage());
        assertFalse(error.getMessage().contains(shortSecret));
    }

    @Test
    void countsUtf8BytesNotCharacters() {
        MockEnvironment environment = environment(POSTGRES_URL, "é".repeat(15), true, "render");

        assertMessage(environment, "JWT_SECRET must be at least 32 UTF-8 bytes.");
    }

    @Test
    void acceptsValid32ByteJwtSecret() {
        MockEnvironment environment = environment(POSTGRES_URL, VALID_SECRET, true, "render");

        assertDoesNotThrow(() -> guard.postProcessEnvironment(environment, null));
    }

    @Test
    void rejectsLeadingWhitespaceInPostgresUrl() {
        MockEnvironment environment = environment(" " + POSTGRES_URL, VALID_SECRET, true, "render");

        assertMessage(environment, "DB_URL must not start or end with whitespace.");
    }

    @Test
    void rejectsTrailingWhitespaceInPostgresUrl() {
        MockEnvironment environment = environment(POSTGRES_URL + " ", VALID_SECRET, true, "render");

        assertMessage(environment, "DB_URL must not start or end with whitespace.");
    }

    @Test
    void rejectsLeadingWhitespaceInH2UrlWithoutProfile() {
        MockEnvironment environment = environment(" " + H2_URL, VALID_SECRET, false);

        assertMessage(environment, "DB_URL must not start or end with whitespace.");
    }

    @Test
    void rejectsTrailingWhitespaceInH2UrlWithLocalProfile() {
        MockEnvironment environment = environment(H2_URL + "\t", VALID_SECRET, false, "local");

        assertMessage(environment, "DB_URL must not start or end with whitespace.");
    }

    @Test
    void allowsH2WithLocalProfileOutsideRender() {
        MockEnvironment environment = environment(H2_URL, VALID_SECRET, false, "local");

        assertDoesNotThrow(() -> guard.postProcessEnvironment(environment, null));
    }

    @Test
    void allowsH2WithTestProfileOutsideRender() {
        MockEnvironment environment = environment(H2_URL, VALID_SECRET, false, "test");

        assertDoesNotThrow(() -> guard.postProcessEnvironment(environment, null));
    }

    @Test
    void rejectsH2WithoutExplicitProfileOutsideRender() {
        MockEnvironment environment = environment(H2_URL, VALID_SECRET, false);

        assertMessage(environment,
                "DB_URL points to H2, which is allowed only with SPRING_PROFILES_ACTIVE=local or test. "
                        + "Configure a persistent PostgreSQL DB_URL.");
    }

    @Test
    void rejectsH2WithDockerProfile() {
        MockEnvironment environment = environment(H2_URL, VALID_SECRET, false, "docker");

        assertMessage(environment, "DB_URL points to H2, which is not allowed for the render or docker profiles.");
    }

    @Test
    void renderFlagOtherThanTrueDoesNotEnableRenderRules() {
        MockEnvironment environment = environment(H2_URL, VALID_SECRET, false, "local");
        environment.setProperty("RENDER", "false");

        assertDoesNotThrow(() -> guard.postProcessEnvironment(environment, null));
    }

    private void assertMessage(MockEnvironment environment, String expected) {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> guard.postProcessEnvironment(environment, null));
        assertEquals(expected, error.getMessage());
    }

    private static MockEnvironment environment(String url, String jwtSecret, boolean onRender, String... profiles) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profiles);
        if (onRender) {
            environment.setProperty("RENDER", "true");
        }
        environment.setProperty("spring.datasource.url", url);
        environment.setProperty("spring.datasource.username", "dummy-user");
        environment.setProperty("spring.datasource.password", "dummy-password");
        environment.setProperty("app.jwt.secret", jwtSecret);
        return environment;
    }
}
