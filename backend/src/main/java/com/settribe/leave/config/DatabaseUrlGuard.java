package com.settribe.leave.config;

import java.util.Arrays;
import java.util.Locale;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;

public class DatabaseUrlGuard implements EnvironmentPostProcessor, Ordered {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String url = environment.getProperty("spring.datasource.url", "");
        if (url.isBlank() || url.contains("${")) {
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
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    private static boolean hasProfile(ConfigurableEnvironment environment, String profile) {
        return Arrays.asList(environment.getActiveProfiles()).contains(profile);
    }

    private static void requireValue(ConfigurableEnvironment environment, String property, String variable) {
        String value = environment.getProperty(property, "");
        if (value.isBlank() || value.contains("${")) {
            throw new IllegalStateException(variable + " is required for the render and docker profiles.");
        }
    }
}
