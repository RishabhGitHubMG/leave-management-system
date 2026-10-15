package com.settribe.leave.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class DatabaseUrlGuardTests {

    private static final String MYSQL_URL = "jdbc:mysql://127.0.0.1:1/leave";

    private final DatabaseUrlGuard guard = new DatabaseUrlGuard();

    @Test
    void rejectsJwtSecretShorterThan32BytesWithoutEchoingIt() {
        String shortSecret = "short-dummy-secret-0123456789ab";
        assertEquals(31, shortSecret.getBytes(StandardCharsets.UTF_8).length);
        MockEnvironment environment = renderEnvironment(MYSQL_URL, shortSecret);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> guard.postProcessEnvironment(environment, null));

        assertEquals("JWT_SECRET must be at least 32 UTF-8 bytes.", error.getMessage());
        assertFalse(error.getMessage().contains(shortSecret));
    }

    @Test
    void countsUtf8BytesNotCharacters() {
        String fifteenTwoByteChars = "é".repeat(15);
        MockEnvironment environment = renderEnvironment(MYSQL_URL, fifteenTwoByteChars);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> guard.postProcessEnvironment(environment, null));

        assertEquals("JWT_SECRET must be at least 32 UTF-8 bytes.", error.getMessage());
    }

    @Test
    void acceptsJwtSecretOf32Bytes() {
        MockEnvironment environment = renderEnvironment(MYSQL_URL, "a".repeat(32));

        assertDoesNotThrow(() -> guard.postProcessEnvironment(environment, null));
    }

    @Test
    void reportsUnresolvedDbUrlPlaceholderAsMissing() {
        MockEnvironment environment = renderEnvironment("${DB_URL}", "a".repeat(32));

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> guard.postProcessEnvironment(environment, null));

        assertEquals("DB_URL is required: set it to a JDBC URL for the database.", error.getMessage());
    }

    private static MockEnvironment renderEnvironment(String url, String jwtSecret) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("render");
        environment.setProperty("spring.datasource.url", url);
        environment.setProperty("spring.datasource.username", "dummy-user");
        environment.setProperty("spring.datasource.password", "dummy-password");
        environment.setProperty("app.jwt.secret", jwtSecret);
        return environment;
    }
}
