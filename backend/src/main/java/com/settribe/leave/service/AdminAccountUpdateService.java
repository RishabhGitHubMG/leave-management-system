package com.settribe.leave.service;

import com.settribe.leave.entity.Role;
import com.settribe.leave.entity.User;
import com.settribe.leave.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class AdminAccountUpdateService {

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$");

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public void updateExistingAdmin(Long userId, String expectedCurrentEmail,
                                    String newEmail, CharSequence newPassword) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("A positive target user ID is required.");
        }
        String expectedEmail = normalizeEmail(expectedCurrentEmail);
        String normalizedNewEmail = normalizeEmail(newEmail);
        if (normalizedNewEmail.length() > 150 || !EMAIL_PATTERN.matcher(normalizedNewEmail).matches()) {
            throw new IllegalArgumentException("The new email address is invalid.");
        }
        if (newPassword == null || newPassword.length() < 8
                || newPassword.toString().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException("The new password must be at least 8 characters and at most 72 UTF-8 bytes.");
        }

        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new IllegalArgumentException("The target user ID was not found."));
        if (user.getRole() != Role.ADMIN || !user.isActive()) {
            throw new IllegalArgumentException("The target account is not an active ADMIN account.");
        }
        if (!normalizeEmail(user.getEmail()).equals(expectedEmail)) {
            throw new IllegalArgumentException("The target ID and expected current email do not match.");
        }
        if (userRepository.existsByEmailAndIdNot(normalizedNewEmail, userId)) {
            throw new IllegalArgumentException("The new email address is already assigned to another account.");
        }

        user.setEmail(normalizedNewEmail);
        user.setPassword(passwordEncoder.encode(newPassword));
        try {
            userRepository.flush();
        } catch (DataIntegrityViolationException exception) {
            throw new IllegalStateException("The update was rejected by a database constraint; no account was changed.");
        }
    }

    private static String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("An email address is required.");
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
