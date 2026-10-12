package com.settribe.leave.config;

import com.settribe.leave.entity.Role;
import com.settribe.leave.entity.User;
import com.settribe.leave.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Startup bootstrapper that automatically seeds a default ADMIN user into the database
 * if one does not already exist.
 * 
 * Implements CommandLineRunner, which runs immediately after the Spring application context starts.
 */
@Component
@RequiredArgsConstructor
public class AdminInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        String adminEmail = "admin@test.com";

        // Check if the default admin account already exists to prevent duplicate insertions
        if (!userRepository.existsByEmail(adminEmail)) {
            User admin = User.builder()
                    .name("System Admin")
                    .email(adminEmail)
                    .password(passwordEncoder.encode("Passw0rd!")) // Password hashed with BCrypt
                    .role(Role.ADMIN)                              // Elevated role: ADMIN
                    .build();
            userRepository.save(admin);
            System.out.println(">>> Created default Admin user: admin@test.com / Passw0rd!");
        }
    }
}

