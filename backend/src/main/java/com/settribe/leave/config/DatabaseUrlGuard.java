package com.settribe.leave.config;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;

public class DatabaseUrlGuard implements EnvironmentPostProcessor, Ordered {

    private static final int MIN_JWT_SECRET_BYTES = 32;

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String url = resolved(environment, "spring.datasource.url");
        if (url.isBlank()) {
            throw new IllegalStateException("DB_URL is required: set it to a JDBC URL for the database.");
        }
        boolean h2Permitted = hasProfile(environment, "local") || hasProfile(environment, "test");
        if (url.toLowerCase(Locale.ROOT).startsWith("jdbc:h2:") && !h2Permitted) {
            throw new IllegalStateException(
                    "DB_URL points to H2, which is allowed only with SPRING_PROFILES_ACTIVE=local or test. "
                            + "Configure a persistent MySQL DB_URL.");
        }
        if (hasProfile(environment, "render") || hasProfile(environment, "docker")) {
            if (!url.startsWith("jdbc:mysql:")) {
                throw new IllegalStateException("DB_URL must be a jdbc:mysql URL for the render and docker profiles.");
            }
            requireValue(environment, "spring.datasource.username", "DB_USER");
            requireValue(environment, "spring.datasource.password", "DB_PASSWORD");
        }
        requireJwtSecret(environment);
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    private static boolean hasProfile(ConfigurableEnvironment environment, String profile) {
        return Arrays.asList(environment.getActiveProfiles()).contains(profile);
    }

    private static void requireValue(ConfigurableEnvironment environment, String property, String variable) {
        if (resolved(environment, property).isBlank()) {
            throw new IllegalStateException(variable + " is required for the render and docker profiles.");
        }
    }

    private static void requireJwtSecret(ConfigurableEnvironment environment) {
        String secret = resolved(environment, "app.jwt.secret");
        if (secret.isBlank()) {
            throw new IllegalStateException("JWT_SECRET is required: set it to a secret of at least 32 bytes.");
        }
        if (secret.getBytes(StandardCharsets.UTF_8).length < MIN_JWT_SECRET_BYTES) {
            throw new IllegalStateException("JWT_SECRET must be at least 32 UTF-8 bytes.");
        }
    }

    // Non-strict: an unset ${VAR} is treated as missing instead of throwing before the guard's message.
    private static String resolved(ConfigurableEnvironment environment, String property) {
        String value = environment.resolvePlaceholders("${" + property + ":}");
        return value.contains("${") ? "" : value;
    }
}
