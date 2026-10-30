package com.settribe.leave.config;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

public class DatabaseUrlGuard implements EnvironmentPostProcessor, Ordered {

    private static final int MIN_JWT_SECRET_BYTES = 32;

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (isOnRender(environment)
                && resolved(environment, "spring.datasource.url").isBlank()
                && !resolved(environment, "DB_CONNECTION_STRING").isBlank()) {
            setRenderJdbcUrl(environment);
        }
        String url = resolved(environment, "spring.datasource.url");
        if (url.isBlank()) {
            throw new IllegalStateException("DB_URL is required: set it to a JDBC URL for the database.");
        }
        if (!url.equals(url.strip())) {
            throw new IllegalStateException("DB_URL must not start or end with whitespace.");
        }
        boolean onRender = isOnRender(environment);
        boolean dockerOrRender = onRender || hasProfile(environment, "render") || hasProfile(environment, "docker");
        if (url.toLowerCase(Locale.ROOT).startsWith("jdbc:h2:")) {
            if (onRender) {
                throw new IllegalStateException("DB_URL points to H2, which is not allowed on Render.");
            }
            if (dockerOrRender) {
                throw new IllegalStateException(
                        "DB_URL points to H2, which is not allowed for the render or docker profiles.");
            }
            if (!hasProfile(environment, "local") && !hasProfile(environment, "test")) {
                throw new IllegalStateException(
                        "DB_URL points to H2, which is allowed only with SPRING_PROFILES_ACTIVE=local or test. "
                                + "Configure a persistent PostgreSQL DB_URL.");
            }
        }
        if (onRender && !hasProfile(environment, "render") && !hasProfile(environment, "admin-account-update")) {
            throw new IllegalStateException("SPRING_PROFILES_ACTIVE=render is required when running on Render.");
        }
        if (dockerOrRender) {
            if (!url.startsWith("jdbc:postgresql:")) {
                throw new IllegalStateException("DB_URL must be a jdbc:postgresql URL on Render and for the render and docker profiles.");
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

    private static void setRenderJdbcUrl(ConfigurableEnvironment environment) {
        String connectionString = resolved(environment, "DB_CONNECTION_STRING");
        try {
            URI connection = new URI(connectionString);
            String authority = connection.getRawAuthority();
            int userInfoEnd = authority == null ? -1 : authority.lastIndexOf('@');
            if (!"postgresql".equalsIgnoreCase(connection.getScheme())
                    || connection.getHost() == null
                    || connection.getPort() < 1
                    || connection.getRawPath() == null
                    || connection.getRawPath().length() < 2
                    || connection.getRawFragment() != null
                    || userInfoEnd < 0) {
                throw new IllegalArgumentException();
            }
            String jdbcUrl = "jdbc:postgresql://" + authority.substring(userInfoEnd + 1) + connection.getRawPath();
            if (connection.getRawQuery() != null) {
                jdbcUrl += "?" + connection.getRawQuery();
            }
            environment.getPropertySources().addFirst(
                    new MapPropertySource("renderDatabaseConnection", Map.of("DB_URL", jdbcUrl)));
        } catch (URISyntaxException | IllegalArgumentException exception) {
            throw new IllegalStateException("DB_CONNECTION_STRING must be a valid PostgreSQL connection string.");
        }
    }

    // Render sets RENDER=true on every runtime, so it is checked independently of SPRING_PROFILES_ACTIVE.
    private static boolean isOnRender(ConfigurableEnvironment environment) {
        return "true".equals(resolved(environment, "RENDER"));
    }

    private static boolean hasProfile(ConfigurableEnvironment environment, String profile) {
        return Arrays.asList(environment.getActiveProfiles()).contains(profile);
    }

    private static void requireValue(ConfigurableEnvironment environment, String property, String variable) {
        if (resolved(environment, property).isBlank()) {
            throw new IllegalStateException(variable + " is required for Render and the render and docker profiles.");
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
