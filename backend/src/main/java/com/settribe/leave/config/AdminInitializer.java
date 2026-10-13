package com.settribe.leave.config;

import com.settribe.leave.entity.Role;
import com.settribe.leave.entity.User;
import com.settribe.leave.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Creates a bootstrap ADMIN account from ADMIN_EMAIL and ADMIN_PASSWORD when both are set
 * and no account with that email exists. Does nothing otherwise.
 */
@Component
@RequiredArgsConstructor
public class AdminInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.email:}")
    private String adminEmail;

    @Value("${app.admin.password:}")
    private String adminPassword;

    @Override
    public void run(String... args) {
        if (adminEmail == null || adminEmail.isBlank() || adminPassword == null || adminPassword.isBlank()) {
            return;
        }
        if (adminPassword.length() < 8) {
            throw new IllegalStateException("ADMIN_PASSWORD must be at least 8 characters");
        }

        String email = adminEmail.trim().toLowerCase();
        if (userRepository.existsByEmail(email)) {
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
