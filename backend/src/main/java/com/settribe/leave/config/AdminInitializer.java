package com.settribe.leave.config;

import com.settribe.leave.entity.Role;
import com.settribe.leave.entity.User;
import com.settribe.leave.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Creates a bootstrap ADMIN account from ADMIN_EMAIL and ADMIN_PASSWORD when both are set
 * and no account with that email exists. Does nothing otherwise.
 */
@Component
@Profile("!admin-account-update")
@RequiredArgsConstructor
public class AdminInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminInitializer.class);
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.email:}")
    private String adminEmail;

    @Value("${app.admin.password:}")
    private String adminPassword;

    @Override
    public void run(String... args) {
        if (adminEmail == null || adminEmail.isBlank() || adminPassword == null || adminPassword.isBlank()) {
            log.warn("No bootstrap admin created: ADMIN_EMAIL and ADMIN_PASSWORD must both be set");
            return;
        }
        if (adminPassword.length() < 8 || adminPassword.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalStateException("ADMIN_PASSWORD must be at least 8 characters and at most 72 UTF-8 bytes");
        }

        String email = adminEmail.trim().toLowerCase();
        var existing = userRepository.findByEmail(email);
        if (existing.isPresent()) {
            User user = existing.get();
            if (user.getRole() != Role.ADMIN || !user.isActive()) {
                log.warn("Configured bootstrap admin {} already exists but is not an active ADMIN account", email);
            }
            return;
        }

        User admin = User.builder()
                .name("System Admin")
                .email(email)
                .password(passwordEncoder.encode(adminPassword))
                .role(Role.ADMIN)
                .department("Executive Management")
                .designation("System Administrator")
                .employeeId("SET-0001")
                .build();
        userRepository.save(admin);
    }
}
