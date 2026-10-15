package com.settribe.leave.config;

import java.util.Arrays;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class DatabaseUrlGuard {

    public DatabaseUrlGuard(Environment environment, @Value("${spring.datasource.url}") String url) {
        boolean h2Permitted = Arrays.stream(environment.getActiveProfiles())
                .anyMatch(profile -> profile.equals("local") || profile.equals("test"));
        if (url.toLowerCase(Locale.ROOT).startsWith("jdbc:h2:") && !h2Permitted) {
            throw new IllegalStateException(
                    "DB_URL points to H2, which is allowed only with SPRING_PROFILES_ACTIVE=local or test. "
                            + "Configure a persistent MySQL DB_URL.");
        }
    }
}
