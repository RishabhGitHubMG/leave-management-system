package com.settribe.leave.config;

import com.settribe.leave.service.AdminAccountUpdateService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Component
@Profile("admin-account-update")
@RequiredArgsConstructor
public class AdminAccountUpdateRunner implements CommandLineRunner {

    private final AdminAccountUpdateService accountUpdateService;
    private final Environment environment;

    @Value("${app.admin-update.user-id}")
    private Long userId;

    @Value("${app.admin-update.expected-current-email}")
    private String expectedCurrentEmail;

    @Value("${app.admin-update.new-email}")
    private String newEmail;

    @Value("${app.admin-update.expected-database-url}")
    private String expectedDatabaseUrl;

    @Value("${spring.jpa.hibernate.ddl-auto}")
    private String ddlAuto;

    @Override
    public void run(String... args) {
        String actualDatabaseUrl = environment.getProperty("spring.datasource.url", "");
        if (!expectedDatabaseUrl.equals(actualDatabaseUrl)) {
            throw new IllegalStateException("Configured database URL does not match the explicitly confirmed target.");
        }
        if (!"validate".equalsIgnoreCase(ddlAuto)) {
            throw new IllegalStateException("The one-shot account update requires Hibernate ddl-auto=validate.");
        }

        java.io.Console console = System.console();
        if (console == null) {
            throw new IllegalStateException("Run this operation from an interactive terminal so the password can be entered without echo.");
        }

        char[] password = console.readPassword("New admin password: ");
        char[] confirmation = null;
        try {
            if (password == null) {
                throw new IllegalArgumentException("Password input was interrupted.");
            }
            confirmation = console.readPassword("Confirm new admin password: ");
            if (confirmation == null || !Arrays.equals(password, confirmation)) {
                throw new IllegalArgumentException("The password entries do not match.");
            }
            accountUpdateService.updateExistingAdmin(userId, expectedCurrentEmail, newEmail,
                    java.nio.CharBuffer.wrap(password));
            console.printf("Admin account updated for user ID %d. Sign in again using the new email.%n", userId);
        } finally {
            Arrays.fill(password, '\0');
            if (confirmation != null) {
                Arrays.fill(confirmation, '\0');
            }
        }
    }
}
